package tr.com.servisdefteri.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.com.servisdefteri.common.CsvUtil;
import tr.com.servisdefteri.payment.Payment;
import tr.com.servisdefteri.payment.PaymentAllocation;
import tr.com.servisdefteri.payment.PaymentService;
import tr.com.servisdefteri.report.DailyReportRow;
import tr.com.servisdefteri.report.DailyReportView;
import tr.com.servisdefteri.settings.BusinessSettings;
import tr.com.servisdefteri.settings.BusinessSettingsService;
import tr.com.servisdefteri.workorder.WorkOrder;
import tr.com.servisdefteri.workorder.WorkOrderItem;
import tr.com.servisdefteri.workorder.VehicleDelivery;
import tr.com.servisdefteri.workorder.VehicleIntake;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * Is emri ve tahsilat makbuzu icin dogrudan indirilebilir PDF uretir.
 * Turkce karakterler icin Noto Sans yazitipi gomulur.
 */
@Service
public class PdfService {

    private static final Color NAVY = new Color(18, 37, 61);
    private static final Color RED = new Color(235, 10, 30);
    private static final Color MUTED = new Color(100, 112, 128);
    private static final Color LIGHT = new Color(246, 248, 251);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String FONT_PATH = "fonts/NotoSans-Regular.ttf";

    private final BusinessSettingsService businessSettingsService;
    private final PaymentService paymentService;
    private final byte[] fontBytes;

    public PdfService(BusinessSettingsService businessSettingsService, PaymentService paymentService) {
        this.businessSettingsService = businessSettingsService;
        this.paymentService = paymentService;
        this.fontBytes = readResource(FONT_PATH);
    }

    @Transactional(readOnly = true)
    public byte[] workOrderPdf(WorkOrder order) {
        BusinessSettings business = businessSettingsService.get();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 40, 40);
            PdfWriter.getInstance(document, output);
            document.open();

            BaseFont baseFont = createBaseFont();
            Font normal = new Font(baseFont, 9, Font.NORMAL, Color.DARK_GRAY);
            Font small = new Font(baseFont, 8, Font.NORMAL, MUTED);
            Font bold = new Font(baseFont, 9, Font.BOLD, NAVY);
            Font title = new Font(baseFont, 16, Font.BOLD, NAVY);

            addHeader(document, business, "İŞ EMRİ", order.getOrderNo(),
                    order.getOpenedAt() == null ? "" : DATE_TIME.format(order.getOpenedAt()),
                    order.getStatus().getLabel(), baseFont);

            PdfPTable parties = new PdfPTable(new float[]{1, 1});
            parties.setWidthPercentage(100);
            parties.setSpacingBefore(10);
            parties.setSpacingAfter(10);
            parties.addCell(infoCell("MÜŞTERİ", order.getCustomer().getName()
                    + "\n" + safe(order.getCustomer().getPhone())
                    + (order.getCustomer().getAddress() == null ? "" : "\n" + order.getCustomer().getAddress()),
                    baseFont, normal, bold));
            parties.addCell(infoCell("ARAÇ", order.getVehicle().getPlate()
                    + "\n" + safe(order.getVehicle().getBrand()) + " " + safe(order.getVehicle().getModel())
                    + (order.getMileage() == null ? "" : "\n" + order.getMileage() + " km"),
                    baseFont, normal, bold));
            document.add(parties);

            addTextSection(document, "Müşteri şikâyeti", order.getComplaint(), normal, bold);
            addTextSection(document, "Tespit edilen arıza", order.getDiagnosis(), normal, bold);
            addTextSection(document, "Yapılan işlemler", order.getWorkDone(), normal, bold);

            Paragraph itemsTitle = new Paragraph("İşlem kalemleri", bold);
            itemsTitle.setSpacingBefore(8);
            itemsTitle.setSpacingAfter(4);
            document.add(itemsTitle);

            PdfPTable items = new PdfPTable(new float[]{1.1f, 4.2f, 1.1f, 1.5f, 1.6f});
            items.setWidthPercentage(100);
            addHeaderCell(items, "Tip", baseFont);
            addHeaderCell(items, "Açıklama", baseFont);
            addHeaderCell(items, "Miktar", baseFont);
            addHeaderCell(items, "Birim Fiyat", baseFont);
            addHeaderCell(items, "Tutar", baseFont);

            int index = 0;
            List<WorkOrderItem> sortedItems = order.getItems().stream()
                    .sorted(Comparator.comparing(WorkOrderItem::getId))
                    .toList();
            for (WorkOrderItem item : sortedItems) {
                boolean shaded = index++ % 2 == 1;
                addBodyCell(items, item.getType().getLabel(), normal, Element.ALIGN_LEFT, shaded);
                addBodyCell(items, item.getDescription(), normal, Element.ALIGN_LEFT, shaded);
                addBodyCell(items, CsvUtil.money(item.getQuantity()), normal, Element.ALIGN_RIGHT, shaded);
                addBodyCell(items, money(item.getUnitPrice()), normal, Element.ALIGN_RIGHT, shaded);
                addBodyCell(items, money(item.getLineTotal()), normal, Element.ALIGN_RIGHT, shaded);
            }
            if (sortedItems.isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("Kalem girilmemiş.", normal));
                empty.setColspan(5);
                empty.setHorizontalAlignment(Element.ALIGN_CENTER);
                empty.setPadding(8);
                items.addCell(empty);
            }
            document.add(items);

            PdfPTable totals = new PdfPTable(new float[]{2, 1});
            totals.setWidthPercentage(55);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totals.setSpacingBefore(8);
            addTotalRow(totals, "İşçilik", money(order.getLaborTotal()), normal, small);
            addTotalRow(totals, "Parça", money(order.getPartsTotal()), normal, small);
            addTotalRow(totals, "GENEL TOPLAM", money(order.getTotalAmount()), bold, bold);
            document.add(totals);

            PdfPTable signatures = new PdfPTable(new float[]{1, 1});
            signatures.setWidthPercentage(100);
            signatures.setSpacingBefore(46);
            signatures.addCell(signatureCell("Teslim eden", baseFont, null));
            signatures.addCell(signatureCell("Müşteri onayı", baseFont, null));
            document.add(signatures);

            Paragraph note = new Paragraph("Bu belge " + safe(business.getName()) + " tarafından "
                    + (order.getOpenedAt() == null ? "" : DATE_TIME.format(order.getOpenedAt()))
                    + " tarihli iş emri için üretilmiştir. Ödeme ve cari hesap bilgileri işletme kayıtlarında tutulur.",
                    small);
            note.setSpacingBefore(14);
            document.add(note);

            document.close();
            return output.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new IllegalStateException("İş emri PDF'i oluşturulamadı.", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] receiptPdf(Payment payment) {
        BusinessSettings business = businessSettingsService.get();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 40, 40);
            PdfWriter.getInstance(document, output);
            document.open();

            BaseFont baseFont = createBaseFont();
            Font normal = new Font(baseFont, 9, Font.NORMAL, Color.DARK_GRAY);
            Font small = new Font(baseFont, 8, Font.NORMAL, MUTED);
            Font bold = new Font(baseFont, 9, Font.BOLD, NAVY);

            addHeader(document, business, "TAHSİLAT MAKBUZU", payment.getReceiptNo(),
                    payment.getPaymentDate() == null ? "" : DATE.format(payment.getPaymentDate()),
                    payment.getStatus().getLabel(), baseFont);

            PdfPTable parties = new PdfPTable(new float[]{1.4f, 0.8f});
            parties.setWidthPercentage(100);
            parties.setSpacingBefore(10);
            parties.setSpacingAfter(10);
            parties.addCell(infoCell("MÜŞTERİ", payment.getCustomer().getName()
                    + "\n" + safe(payment.getCustomer().getPhone())
                    + (payment.getCustomer().getAddress() == null ? "" : "\n" + payment.getCustomer().getAddress()),
                    baseFont, normal, bold));
            parties.addCell(infoCell("ÖDEME", money(payment.getAmount())
                    + "\n" + (payment.getMethod() == null ? "" : payment.getMethod().getLabel()),
                    baseFont, normal, bold));
            document.add(parties);

            addTextSection(document, "Açıklama", payment.getDescription(), normal, bold);

            var allocations = paymentService.allocations(payment.getId());
            if (!allocations.isEmpty()) {
                Paragraph title = new Paragraph("Borç dağıtımı", bold);
                title.setSpacingBefore(6);
                title.setSpacingAfter(4);
                document.add(title);

                PdfPTable table = new PdfPTable(new float[]{1.2f, 4, 1.6f});
                table.setWidthPercentage(100);
                addHeaderCell(table, "Borç Tarihi", baseFont);
                addHeaderCell(table, "Açıklama", baseFont);
                addHeaderCell(table, "Dağıtılan Tutar", baseFont);
                int index = 0;
                for (PaymentAllocation allocation : allocations) {
                    boolean shaded = index++ % 2 == 1;
                    var transaction = allocation.getTransaction();
                    addBodyCell(table, transaction.getTransactionDate() == null ? ""
                            : DATE.format(transaction.getTransactionDate()), normal, Element.ALIGN_LEFT, shaded);
                    addBodyCell(table, safe(transaction.getDescription()), normal, Element.ALIGN_LEFT, shaded);
                    addBodyCell(table, money(allocation.getAmount()), normal, Element.ALIGN_RIGHT, shaded);
                }
                document.add(table);
            }

            PdfPTable signatures = new PdfPTable(new float[]{1, 1});
            signatures.setWidthPercentage(100);
            signatures.setSpacingBefore(46);
            signatures.addCell(signatureCell("Tahsilatı yapan", baseFont, null));
            signatures.addCell(signatureCell("Müşteri imzası", baseFont, null));
            document.add(signatures);

            Paragraph note = new Paragraph("Bu makbuz " + safe(business.getName()) + " tarafından "
                    + (payment.getCreatedAt() == null ? "" : DATE_TIME.format(payment.getCreatedAt()))
                    + " tarihinde oluşturulmuştur. İptal edilen tahsilatlar için ters kayıt işlenir.", small);
            note.setSpacingBefore(14);
            document.add(note);

            document.close();
            return output.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new IllegalStateException("Tahsilat makbuzu PDF'i oluşturulamadı.", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] dailyReportPdf(DailyReportView report) {
        BusinessSettings business = businessSettingsService.get();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
            PdfWriter.getInstance(document, output);
            document.open();

            BaseFont baseFont = createBaseFont();
            Font normal = new Font(baseFont, 7.5f, Font.NORMAL, Color.DARK_GRAY);
            Font small = new Font(baseFont, 7f, Font.NORMAL, MUTED);
            Font bold = new Font(baseFont, 8f, Font.BOLD, NAVY);

            addHeader(document, business, "GÜNLÜK İŞ DÖKÜMÜ", DATE.format(report.date()),
                    report.rows().size() + " iş", "", baseFont);

            Paragraph summary = new Paragraph(
                    "İş emri toplamı: " + money(report.totalAmount())
                            + "   ·   Tahsilat: " + money(report.totalPaid())
                            + "   ·   Kalan: " + money(report.totalRemaining()),
                    bold);
            summary.setSpacingAfter(6);
            document.add(summary);

            PdfPTable table = new PdfPTable(new float[]{1.1f, 0.7f, 1.0f, 1.6f, 2.9f,
                    1.1f, 0.9f, 1.0f, 1.0f, 1.0f});
            table.setWidthPercentage(100);
            table.setHeaderRows(1);
            addHeaderCell(table, "Fiş No", baseFont);
            addHeaderCell(table, "Saat", baseFont);
            addHeaderCell(table, "Plaka", baseFont);
            addHeaderCell(table, "Müşteri", baseFont);
            addHeaderCell(table, "Yapılan İş", baseFont);
            addHeaderCell(table, "İlgilenen", baseFont);
            addHeaderCell(table, "Durum", baseFont);
            addHeaderCell(table, "Toplam", baseFont);
            addHeaderCell(table, "Tahsilat", baseFont);
            addHeaderCell(table, "Kalan", baseFont);

            int index = 0;
            for (DailyReportRow row : report.rows()) {
                boolean shaded = index++ % 2 == 1;
                addBodyCell(table, row.orderNo(), normal, Element.ALIGN_LEFT, shaded);
                addBodyCell(table, row.openedAt() == null ? "" : TIME.format(row.openedAt()),
                        normal, Element.ALIGN_LEFT, shaded);
                addBodyCell(table, row.plate(), normal, Element.ALIGN_LEFT, shaded);
                addBodyCell(table, row.customerName(), normal, Element.ALIGN_LEFT, shaded);
                addBodyCell(table, row.workDone(), normal, Element.ALIGN_LEFT, shaded);
                addBodyCell(table, row.assignedName(), normal, Element.ALIGN_LEFT, shaded);
                addBodyCell(table, row.statusLabel(), normal, Element.ALIGN_LEFT, shaded);
                addBodyCell(table, money(row.total()), normal, Element.ALIGN_RIGHT, shaded);
                addBodyCell(table, money(row.paid()), normal, Element.ALIGN_RIGHT, shaded);
                addBodyCell(table, money(row.remaining()), normal, Element.ALIGN_RIGHT, shaded);
            }
            if (report.rows().isEmpty()) {
                PdfPCell empty = new PdfPCell(new Phrase("Bu tarihte açılan iş emri yok.", normal));
                empty.setColspan(10);
                empty.setHorizontalAlignment(Element.ALIGN_CENTER);
                empty.setPadding(10);
                table.addCell(empty);
            }
            document.add(table);

            Paragraph note = new Paragraph("Bu döküm " + safe(business.getName())
                    + " tarafından " + DATE.format(report.date())
                    + " tarihinde açılan iş emirlerini içerir. "
                    + "Kalan = iş emri toplamı - tahsilat; cari borç yalnızca tamamlanan işlerde oluşur.",
                    small);
            note.setSpacingBefore(12);
            document.add(note);

            document.close();
            return output.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new IllegalStateException("Günlük döküm PDF'i oluşturulamadı.", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] intakePdf(WorkOrder order, VehicleIntake intake) {
        BusinessSettings business = businessSettingsService.get();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 40, 40);
            PdfWriter.getInstance(document, output);
            document.open();

            BaseFont baseFont = createBaseFont();
            Font normal = new Font(baseFont, 9, Font.NORMAL, Color.DARK_GRAY);
            Font small = new Font(baseFont, 8, Font.NORMAL, MUTED);
            Font bold = new Font(baseFont, 9, Font.BOLD, NAVY);

            addHeader(document, business, "ARAÇ KABUL FORMU", order.getOrderNo(),
                    intake.getIntakeAt() == null ? "" : DATE_TIME.format(intake.getIntakeAt()),
                    order.getStatus().getLabel(), baseFont);
            addParties(document, order, baseFont, normal, bold);

            PdfPTable table = new PdfPTable(new float[]{1.2f, 2});
            table.setWidthPercentage(100);
            table.setSpacingBefore(6);
            addHeaderCell(table, "Kabul Bilgisi", baseFont);
            addHeaderCell(table, "Değer", baseFont);
            addBodyCell(table, "Yakıt seviyesi", normal, Element.ALIGN_LEFT, false);
            addBodyCell(table, intake.getFuelLevel().getLabel(), normal, Element.ALIGN_LEFT, false);
            addBodyCell(table, "Kilometre", normal, Element.ALIGN_LEFT, true);
            addBodyCell(table, order.getMileage() == null ? "—" : order.getMileage() + " km",
                    normal, Element.ALIGN_LEFT, true);
            if (intake.getValuables() != null) {
                addBodyCell(table, "Araçta bırakılan eşyalar", normal, Element.ALIGN_LEFT, false);
                addBodyCell(table, intake.getValuables(), normal, Element.ALIGN_LEFT, false);
            }
            document.add(table);

            addTextSection(document, "Hasar / mevcut durum notu", intake.getDamageNotes(), normal, bold);

            Paragraph statement = new Paragraph("Yukarıda belirtilen araç, kabul sırasındaki mevcut durumuyla "
                    + "servise teslim edilmiştir. Araçta bulunan hasar ve eşyalar bu formda kayıt altına alınmıştır.",
                    normal);
            statement.setSpacingBefore(22);
            document.add(statement);

            addSignatures(document, "Aracı teslim eden", "Servis teslim alan", baseFont,
                    intake.getSignatureData(), null);
            addDocumentFooter(document, business, baseFont, small,
                    "Bu form " + safe(business.getName()) + " tarafından "
                            + (intake.getIntakeAt() == null ? "" : DATE_TIME.format(intake.getIntakeAt()))
                            + " tarihinde düzenlenmiştir.");

            document.close();
            return output.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new IllegalStateException("Araç kabul formu PDF'i oluşturulamadı.", ex);
        }
    }

    @Transactional(readOnly = true)
    public byte[] deliveryPdf(WorkOrder order, VehicleDelivery delivery) {
        BusinessSettings business = businessSettingsService.get();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 40, 40);
            PdfWriter.getInstance(document, output);
            document.open();

            BaseFont baseFont = createBaseFont();
            Font normal = new Font(baseFont, 9, Font.NORMAL, Color.DARK_GRAY);
            Font small = new Font(baseFont, 8, Font.NORMAL, MUTED);
            Font bold = new Font(baseFont, 9, Font.BOLD, NAVY);

            addHeader(document, business, "ARAÇ TESLİM TUTANAĞI", order.getOrderNo(),
                    delivery.getDeliveredAt() == null ? "" : DATE_TIME.format(delivery.getDeliveredAt()),
                    order.getStatus().getLabel(), baseFont);
            addParties(document, order, baseFont, normal, bold);

            PdfPTable table = new PdfPTable(new float[]{1.2f, 2});
            table.setWidthPercentage(100);
            table.setSpacingBefore(6);
            addHeaderCell(table, "Teslim Bilgisi", baseFont);
            addHeaderCell(table, "Değer", baseFont);
            addBodyCell(table, "Teslim alan", normal, Element.ALIGN_LEFT, false);
            addBodyCell(table, safe(delivery.getReceiverName()), normal, Element.ALIGN_LEFT, false);
            addBodyCell(table, "Kilometre", normal, Element.ALIGN_LEFT, true);
            addBodyCell(table, delivery.getMileage() == null ? "—" : delivery.getMileage() + " km",
                    normal, Element.ALIGN_LEFT, true);
            addBodyCell(table, "Yakıt seviyesi", normal, Element.ALIGN_LEFT, false);
            addBodyCell(table, delivery.getFuelLevel().getLabel(), normal, Element.ALIGN_LEFT, false);
            document.add(table);

            addTextSection(document, "Yapılan işlemler", order.getWorkDone(), normal, bold);
            addTextSection(document, "Teslim notu", delivery.getNotes(), normal, bold);

            PdfPTable totals = new PdfPTable(new float[]{2, 1});
            totals.setWidthPercentage(55);
            totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totals.setSpacingBefore(10);
            addTotalRow(totals, "GENEL TOPLAM", money(order.getTotalAmount()), bold, bold);
            document.add(totals);

            Paragraph statement = new Paragraph("Aracı yukarıda belirtilen kilometre ve yakıt seviyesiyle, "
                    + "yapılan işlemler tamamlanmış olarak teslim aldım. Teslim sonrası oluşabilecek hasarlardan "
                    + "işletme sorumlu değildir.", normal);
            statement.setSpacingBefore(22);
            document.add(statement);

            addSignatures(document, "Aracı teslim eden", "Aracı teslim alan", baseFont,
                    null, delivery.getSignatureData());
            addDocumentFooter(document, business, baseFont, small,
                    "Bu tutanak " + safe(business.getName()) + " tarafından "
                            + (delivery.getDeliveredAt() == null ? "" : DATE_TIME.format(delivery.getDeliveredAt()))
                            + " tarihinde düzenlenmiştir.");

            document.close();
            return output.toByteArray();
        } catch (DocumentException | IOException ex) {
            throw new IllegalStateException("Teslim tutanağı PDF'i oluşturulamadı.", ex);
        }
    }

    private void addParties(Document document, WorkOrder order, BaseFont baseFont, Font normal, Font bold)
            throws DocumentException {
        PdfPTable parties = new PdfPTable(new float[]{1, 1});
        parties.setWidthPercentage(100);
        parties.setSpacingBefore(10);
        parties.setSpacingAfter(10);
        parties.addCell(infoCell("MÜŞTERİ", order.getCustomer().getName()
                + "\n" + safe(order.getCustomer().getPhone()), baseFont, normal, bold));
        parties.addCell(infoCell("ARAÇ", order.getVehicle().getPlate()
                + "\n" + safe(order.getVehicle().getBrand()) + " " + safe(order.getVehicle().getModel()),
                baseFont, normal, bold));
        document.add(parties);
    }

    private void addSignatures(Document document, String left, String right, BaseFont baseFont,
                               String leftSignature, String rightSignature)
            throws DocumentException {
        PdfPTable signatures = new PdfPTable(new float[]{1, 1});
        signatures.setWidthPercentage(100);
        signatures.setSpacingBefore(46);
        signatures.addCell(signatureCell(left, baseFont, leftSignature));
        signatures.addCell(signatureCell(right, baseFont, rightSignature));
        document.add(signatures);
    }

    private void addDocumentFooter(Document document, BusinessSettings business, BaseFont baseFont,
                                   Font small, String note) throws DocumentException {
        Paragraph noteParagraph = new Paragraph(note, small);
        noteParagraph.setSpacingBefore(14);
        document.add(noteParagraph);
    }

    private void addHeader(Document document, BusinessSettings business, String documentType,
                           String documentNo, String date, String status, BaseFont baseFont)
            throws DocumentException {
        Font nameFont = new Font(baseFont, 13, Font.BOLD, NAVY);
        Font metaFont = new Font(baseFont, 8, Font.NORMAL, MUTED);
        Font typeFont = new Font(baseFont, 14, Font.BOLD, RED);
        Font noFont = new Font(baseFont, 9, Font.BOLD, NAVY);

        PdfPTable header = new PdfPTable(new float[]{2.2f, 1});
        header.setWidthPercentage(100);

        PdfPCell left = new PdfPCell();
        left.setBorder(Rectangle.NO_BORDER);
        Image logo = brandLogo(business);
        if (logo != null) {
            left.addElement(logo);
        }
        Paragraph businessName = new Paragraph(safe(business.getName()), nameFont);
        businessName.setSpacingBefore(4);
        left.addElement(businessName);
        left.addElement(new Paragraph(contactLine(business), metaFont));
        if (business.getAddress() != null && !business.getAddress().isBlank()) {
            left.addElement(new Paragraph(business.getAddress(), metaFont));
        }
        if (business.getTaxNumber() != null && !business.getTaxNumber().isBlank()) {
            left.addElement(new Paragraph("VKN: " + business.getTaxNumber()
                    + (business.getTaxOffice() == null ? "" : " · " + business.getTaxOffice()), metaFont));
        }
        header.addCell(left);

        PdfPCell right = new PdfPCell();
        right.setBorder(Rectangle.NO_BORDER);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph type = new Paragraph(documentType, typeFont);
        type.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(type);
        Paragraph no = new Paragraph(documentNo, noFont);
        no.setAlignment(Element.ALIGN_RIGHT);
        no.setSpacingBefore(4);
        right.addElement(no);
        Paragraph dateParagraph = new Paragraph(date, metaFont);
        dateParagraph.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(dateParagraph);
        Paragraph statusParagraph = new Paragraph(status, new Font(baseFont, 8, Font.BOLD, NAVY));
        statusParagraph.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(statusParagraph);
        header.addCell(right);

        document.add(header);

        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        PdfPCell ruleCell = new PdfPCell();
        ruleCell.setFixedHeight(3);
        ruleCell.setBorder(Rectangle.NO_BORDER);
        ruleCell.setBackgroundColor(RED);
        rule.addCell(ruleCell);
        document.add(rule);
        document.add(new Paragraph(" "));
    }

    private PdfPCell infoCell(String label, String value, BaseFont baseFont, Font normal, Font bold) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(LIGHT);
        cell.setPadding(10);
        Paragraph title = new Paragraph(label, new Font(baseFont, 7, Font.BOLD, MUTED));
        title.setSpacingAfter(3);
        cell.addElement(title);
        cell.addElement(new Paragraph(value == null ? "" : value, normal));
        return cell;
    }

    private void addTextSection(Document document, String title, String value, Font normal, Font bold)
            throws DocumentException {
        if (value == null || value.isBlank()) {
            return;
        }
        Paragraph titleParagraph = new Paragraph(title, bold);
        titleParagraph.setSpacingBefore(8);
        titleParagraph.setSpacingAfter(2);
        document.add(titleParagraph);
        document.add(new Paragraph(value, normal));
    }

    private void addHeaderCell(PdfPTable table, String text, BaseFont baseFont) {
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(baseFont, 8, Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(NAVY);
        cell.setPadding(6);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, Font font, int alignment, boolean shaded) {
        PdfPCell cell = new PdfPCell(new Phrase(text == null ? "" : text, font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        if (shaded) {
            cell.setBackgroundColor(LIGHT);
        }
        table.addCell(cell);
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setPadding(4);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(4);
        table.addCell(valueCell);
    }

    private PdfPCell signatureCell(String label, BaseFont baseFont, String signatureData) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(24);
        if (signatureData != null && signatureData.startsWith("data:image/png;base64,")) {
            try {
                byte[] png = Base64.getDecoder().decode(
                        signatureData.substring("data:image/png;base64,".length()));
                Image image = Image.getInstance(png);
                image.scaleToFit(160, 55);
                cell.addElement(image);
            } catch (RuntimeException | IOException ignored) {
                // Bozuk imza verisi belgeyi engellemez.
            }
        }
        cell.addElement(new Paragraph("________________________________", new Font(baseFont, 8, Font.NORMAL, MUTED)));
        Paragraph paragraph = new Paragraph(label, new Font(baseFont, 8, Font.NORMAL, MUTED));
        paragraph.setSpacingBefore(2);
        cell.addElement(paragraph);
        return cell;
    }

    private BaseFont createBaseFont() throws DocumentException, IOException {
        return BaseFont.createFont("NotoSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                true, fontBytes, null);
    }

    private String contactLine(BusinessSettings business) {
        StringBuilder sb = new StringBuilder();
        if (business.getPhone() != null && !business.getPhone().isBlank()) {
            sb.append(business.getPhone());
        }
        if (business.getEmail() != null && !business.getEmail().isBlank()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(business.getEmail());
        }
        return sb.toString();
    }

    private String money(BigDecimal value) {
        return CsvUtil.money(value) + " ₺";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private byte[] readResource(String path) {
        try (InputStream input = new ClassPathResource(path).getInputStream()) {
            return input.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Kaynak okunamadı: " + path, ex);
        }
    }

    private Image brandLogo(BusinessSettings business) {
        try {
            byte[] data = business.getLogoData();
            Image image = data != null && data.length > 0
                    ? Image.getInstance(data)
                    : Image.getInstance(readResource("static/images/toyota.png"));
            image.scaleToFit(64, 48);
            image.setAlignment(Element.ALIGN_LEFT);
            return image;
        } catch (RuntimeException | IOException ex) {
            return null;
        }
    }
}
