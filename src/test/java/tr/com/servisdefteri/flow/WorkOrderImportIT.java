package tr.com.servisdefteri.flow;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import tr.com.servisdefteri.security.Role;
import tr.com.servisdefteri.security.User;
import tr.com.servisdefteri.support.BusinessTestSupport;
import tr.com.servisdefteri.workorder.WorkOrderController;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkOrderImportIT extends BusinessTestSupport {

    @Test
    void importsCsvRowsAndCreatesNewCustomerVehicle() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);
        var existingCustomer = createCustomer("Mevcut Müşteri", "0555 444 55 66");
        createVehicle(existingCustomer, "34 IMP 002");

        String csv = """
                Fiş No;Plaka;Müşteri;Telefon;Şikâyet;Yapılacak İş;İlgilenen;Kilometre
                IMP-001;34 IMP 001;İçe Aktarım Yeni;0555 444 55 66;Fren sesi;Fren bakımı;Ahmet Usta;12000
                IMP-002;34 IMP 002;;;Yağ bakımı;Yağ değişimi;Mehmet Usta;13000
                """;
        MockMultipartFile file = new MockMultipartFile("file", "isler.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/work-orders/import").file(file)
                        .with(user(principal(servis)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/work-orders"));

        assertThat(count("select count(*) from work_orders where order_no in ('IMP-001','IMP-002')"))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
                select assigned_name from work_orders where order_no = 'IMP-001'
                """, String.class)).isEqualTo("Ahmet Usta");
        assertThat(count("select count(*) from customers where name = 'İçe Aktarım Yeni'"))
                .isEqualTo(1);
    }

    @Test
    void previewDoesNotCreateUntilConfirmed() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);
        var customer = createCustomer("Önizleme Müşteri", "0555 446 55 66");
        createVehicle(customer, "34 IMP 004");

        String csv = """
                Fiş No;Plaka;Müşteri;Telefon;Şikâyet;Yapılacak İş
                IMP-PREVIEW-001;34 IMP 004;;;Fren sesi;Fren bakımı
                """;
        MockMultipartFile file = new MockMultipartFile("file", "onizleme.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(multipart("/work-orders/import/preview").file(file).session(session)
                        .with(user(principal(servis)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("İçe Aktarma Önizlemesi")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("IMP-PREVIEW-001")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Onayla ve İçe Aktar")));

        assertThat(count("select count(*) from work_orders where order_no = 'IMP-PREVIEW-001'"))
                .isZero();
        String token = (String) session.getAttribute(WorkOrderController.SESSION_IMPORT_TOKEN);
        assertThat(token).isNotBlank();

        mockMvc.perform(post("/work-orders/import/confirm").session(session)
                        .with(user(principal(servis)))
                        .with(csrf())
                        .param("previewToken", token))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/work-orders"));

        assertThat(count("select count(*) from work_orders where order_no = 'IMP-PREVIEW-001'"))
                .isEqualTo(1);
    }

    @Test
    void confirmWithoutPreviewCreatesNothing() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);

        mockMvc.perform(post("/work-orders/import/confirm")
                        .with(user(principal(servis)))
                        .with(csrf())
                        .param("previewToken", "gecersiz"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Önizleme bulunamadı")));

        assertThat(count("select count(*) from work_orders")).isZero();
    }

    @Test
    void duplicateOrderNumberInFileCreatesNothing() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);
        var customer = createCustomer("Mükerrer Müşteri", "0555 445 55 66");
        createVehicle(customer, "34 IMP 003");

        String csv = """
                Fiş No;Plaka;Müşteri;Telefon;Şikâyet;Yapılacak İş
                IMP-DUP-001;34 IMP 003;;;Fren;Fren bakımı
                IMP-DUP-001;34 IMP 003;;;Yağ;Yağ değişimi
                """;
        MockMultipartFile file = new MockMultipartFile("file", "mukerrer.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/work-orders/import").file(file)
                        .with(user(principal(servis)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("tekrar ediyor")));

        assertThat(count("select count(*) from work_orders where order_no = 'IMP-DUP-001'"))
                .isZero();
    }

    @Test
    void importsXlsxRows() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);
        byte[] xlsx = workbookBytes();
        MockMultipartFile file = new MockMultipartFile("file", "isler.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsx);

        mockMvc.perform(multipart("/work-orders/import").file(file)
                        .with(user(principal(servis)))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/work-orders"));

        assertThat(count("select count(*) from work_orders where order_no = 'IMP-XLSX-001'"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                select assigned_name from work_orders where order_no = 'IMP-XLSX-001'
                """, String.class)).isEqualTo("Excel Usta");
    }

    @Test
    void templateDownloadHasUtf8BomAndExpectedColumns() throws Exception {
        User servis = createUser("servis", "Servis Kullanıcısı", Role.SERVIS);

        MvcResult result = mockMvc.perform(get("/work-orders/import/template")
                        .with(user(principal(servis))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("is-emri-ice-aktarma-sablonu.csv")))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body.length).isGreaterThan(3);
        assertThat(body[0]).isEqualTo((byte) 0xEF);
        assertThat(body[1]).isEqualTo((byte) 0xBB);
        assertThat(body[2]).isEqualTo((byte) 0xBF);
        assertThat(new String(body, StandardCharsets.UTF_8)).contains("Fiş No;Plaka");
    }

    @Test
    void muhasebeCannotOpenImportScreens() throws Exception {
        User muhasebe = createUser("muhasebe", "Muhasebe Kullanıcısı", Role.MUHASEBE);

        mockMvc.perform(get("/work-orders/import").with(user(principal(muhasebe))))
                .andExpect(status().isForbidden());
    }

    private byte[] workbookBytes() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("İşler");
            Row header = sheet.createRow(0);
            String[] headers = {"Fiş No", "Plaka", "Müşteri", "Telefon", "Şikâyet",
                    "Yapılacak İş", "İlgilenen", "Kilometre"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row row = sheet.createRow(1);
            String[] values = {"IMP-XLSX-001", "34 XLS 001", "Excel Müşteri", "0555 555 55 55",
                    "Motor sesi", "Kontrol", "Excel Usta", "5000"};
            for (int i = 0; i < values.length; i++) {
                row.createCell(i).setCellValue(values[i]);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
