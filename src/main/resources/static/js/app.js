(function () {
    'use strict';

    function onReady(fn) {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', fn);
        } else {
            fn();
        }
    }

    function initPaymentAllocator() {
        var form = document.querySelector('[data-payment-form]');
        if (!form) {
            return;
        }
        var amountInput = form.querySelector('[data-payment-amount]');
        var rows = Array.prototype.slice.call(form.querySelectorAll('[data-allocation-amount]'));
        var summary = document.getElementById('allocationSummary');
        var money = function (value) {
            return new Intl.NumberFormat('tr-TR', {style: 'currency', currency: 'TRY'}).format(value || 0);
        };
        var parseAmount = function (value) {
            if (!value) {
                return 0;
            }
            var normalized = String(value).trim();
            if (normalized.indexOf(',') >= 0) {
                normalized = normalized.replace(/\./g, '').replace(',', '.');
            }
            var parsed = parseFloat(normalized);
            return isNaN(parsed) ? 0 : parsed;
        };
        var updateSummary = function () {
            if (!summary) {
                return;
            }
            var allocated = rows.reduce(function (total, input) {
                return total + parseAmount(input.value);
            }, 0);
            var amount = parseAmount(amountInput ? amountInput.value : 0);
            if (amount <= 0 && allocated <= 0) {
                summary.className = 'toast-inline mt-3';
                summary.textContent = 'Tahsilat tutarını girin; dağıtımı isterseniz elle veya “Kalanı Dağıt” ile yapın.';
                return;
            }
            var diff = Math.round((amount - allocated) * 100) / 100;
            if (diff > 0.004) {
                summary.className = 'toast-inline mt-3';
                summary.textContent = money(allocated) + ' dağıtıldı; ' + money(diff)
                    + ' avans olarak cari hesaba işlenecek.';
            } else if (diff < -0.004) {
                summary.className = 'alert alert-danger mt-3 mb-0 py-2';
                summary.textContent = money(allocated) + ' dağıtım, tahsilat tutarından '
                    + money(Math.abs(diff)) + ' fazla.';
            } else {
                summary.className = 'alert alert-success mt-3 mb-0 py-2';
                summary.textContent = 'Tahsilatın tamamı borçlara dağıtıldı.';
            }
        };
        var allocate = function (useOpenDebtTotal) {
            var amount = useOpenDebtTotal
                ? parseFloat(form.getAttribute('data-open-debt-total') || '0')
                : parseAmount(amountInput ? amountInput.value : 0);
            if (!amount || amount <= 0) {
                if (amountInput) {
                    amountInput.focus();
                }
                return;
            }
            if (useOpenDebtTotal && amountInput) {
                amountInput.value = amount.toFixed(2);
            }
            var remaining = amount;
            rows.forEach(function (input) {
                var cap = parseFloat(input.getAttribute('data-remaining') || '0');
                var value = Math.max(0, Math.min(remaining, cap));
                input.value = value > 0 ? value.toFixed(2) : '';
                remaining = Math.round((remaining - value) * 100) / 100;
            });
            updateSummary();
        };
        var singleDebtAllocation = form.querySelector('[data-single-debt-allocation]');
        var syncSingleDebtAllocation = function () {
            if (!singleDebtAllocation) {
                return;
            }
            var remaining = parseFloat(singleDebtAllocation.getAttribute('data-single-debt-remaining') || '0');
            var amount = parseAmount(amountInput ? amountInput.value : 0);
            var allocated = Math.max(0, Math.min(amount, remaining));
            singleDebtAllocation.value = allocated > 0 ? allocated.toFixed(2) : '0';
        };
        if (amountInput) {
            amountInput.addEventListener('input', function () {
                syncSingleDebtAllocation();
                updateSummary();
            });
        }
        rows.forEach(function (input) {
            input.addEventListener('input', updateSummary);
        });
        syncSingleDebtAllocation();
        form.querySelectorAll('[data-allocate-remaining]').forEach(function (button) {
            button.addEventListener('click', function () {
                allocate(false);
            });
        });
        form.querySelectorAll('[data-fill-open-debt]').forEach(function (button) {
            button.addEventListener('click', function () {
                allocate(true);
            });
        });
        updateSummary();
    }

    onReady(function () {
        var body = document.body;
        var toggle = document.querySelector('[data-sidebar-toggle]');
        var overlay = document.querySelector('[data-sidebar-overlay]');
        var sidebar = document.querySelector('.sd-sidebar');
        var setSidebarState = function (open) {
            body.classList.toggle('sidebar-open', open);
            if (toggle) {
                toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
            }
            if (sidebar) {
                var offCanvas = window.matchMedia('(max-width: 991px)').matches;
                var hidden = offCanvas && !open;
                sidebar.setAttribute('aria-hidden', hidden ? 'true' : 'false');
                if ('inert' in sidebar) {
                    sidebar.inert = hidden;
                }
            }
        };
        var closeSidebar = function () {
            setSidebarState(false);
        };

        setSidebarState(body.classList.contains('sidebar-open'));
        if (toggle) {
            toggle.addEventListener('click', function () {
                setSidebarState(!body.classList.contains('sidebar-open'));
            });
        }
        if (overlay) {
            overlay.addEventListener('click', closeSidebar);
        }
        document.querySelectorAll('[data-sidebar-close]').forEach(function (button) {
            button.addEventListener('click', closeSidebar);
        });
        document.addEventListener('click', function (event) {
            if (!body.classList.contains('sidebar-open')) {
                return;
            }
            if (sidebar && sidebar.contains(event.target)) {
                return;
            }
            if (toggle && toggle.contains(event.target)) {
                return;
            }
            closeSidebar();
        });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') {
                closeSidebar();
            }
        });

        var mobileNav = document.querySelector('[data-mobile-nav]');
        if (mobileNav) {
            var path = window.location.pathname;
            mobileNav.querySelectorAll('a[href]').forEach(function (link) {
                var href = link.getAttribute('href');
                var active = false;
                if (href === '/') {
                    active = path === '/';
                } else if (href && (path === href || path.indexOf(href + '/') === 0)) {
                    active = true;
                }
                if (link.hasAttribute('data-mobile-board-home')
                    && path.indexOf('/work-orders/board') === 0) {
                    active = true;
                }
                if (active && !link.classList.contains('mobile-nav-primary')) {
                    link.classList.add('is-active');
                }
            });
        }

        document.querySelectorAll('[data-assigned-suggestion]').forEach(function (chip) {
            chip.addEventListener('click', function () {
                var field = chip.closest('.assigned-field');
                var input = field ? field.querySelector('[data-assigned-name-input]') : null;
                if (!input) {
                    return;
                }
                input.value = chip.getAttribute('data-assigned-suggestion') || '';
                input.dispatchEvent(new Event('input', { bubbles: true }));
                input.focus();
            });
        });

        document.querySelectorAll('[data-password-toggle]').forEach(function (button) {
            button.addEventListener('click', function () {
                var targetId = button.getAttribute('data-password-toggle');
                var input = document.getElementById(targetId);
                if (!input) {
                    return;
                }
                var show = input.type === 'password';
                input.type = show ? 'text' : 'password';
                button.setAttribute('aria-label', show ? 'Parolayı gizle' : 'Parolayı göster');
                var icon = button.querySelector('i');
                if (icon) {
                    icon.className = show ? 'bi bi-eye-slash' : 'bi bi-eye';
                }
            });
        });

        document.querySelectorAll('form[data-confirm]').forEach(function (form) {
            form.addEventListener('submit', function (event) {
                var message = form.getAttribute('data-confirm');
                if (!window.confirm(message)) {
                    event.preventDefault();
                }
            });
        });

        document.querySelectorAll('form[data-submit-once]').forEach(function (form) {
            form.addEventListener('submit', function (event) {
                if (event.defaultPrevented || form.dataset.submitting) {
                    event.preventDefault();
                    return;
                }
                var button = event.submitter || form.querySelector('button[type="submit"]');
                form.dataset.submitting = 'true';
                if (button && button.name) {
                    var copy = document.createElement('input');
                    copy.type = 'hidden';
                    copy.name = button.name;
                    copy.value = button.value;
                    form.appendChild(copy);
                }
                if (button) {
                    button.disabled = true;
                    button.classList.add('disabled');
                    var spinner = button.querySelector('.spinner-border');
                    if (spinner) {
                        spinner.classList.remove('d-none');
                    }
                }
            });
        });

        var customerSelect = document.getElementById('customerSelect');
        var vehicleSelect = document.getElementById('vehicleSelect');
        if (customerSelect && vehicleSelect) {
            var filter = function () {
                var customerId = customerSelect.value;
                var selected = vehicleSelect.value;
                var visible = 0;
                Array.prototype.forEach.call(vehicleSelect.options, function (option) {
                    if (!option.value) {
                        return;
                    }
                    var matches = !customerId || option.getAttribute('data-customer') === customerId;
                    option.hidden = !matches;
                    option.disabled = !matches;
                    if (matches) {
                        visible++;
                    }
                });
                if (selected) {
                    var current = Array.prototype.find.call(vehicleSelect.options, function (option) {
                        return option.value === selected;
                    });
                    if (current && current.disabled) {
                        vehicleSelect.value = '';
                    }
                }
                var empty = vehicleSelect.querySelector('option[data-empty]');
                if (empty) {
                    empty.hidden = visible > 0;
                }
            };
            customerSelect.addEventListener('change', filter);
            filter();
        }

        var mileageInput = document.getElementById('mileage');
        if (vehicleSelect && mileageInput) {
            vehicleSelect.addEventListener('change', function () {
                var option = vehicleSelect.options[vehicleSelect.selectedIndex];
                var mileage = option ? option.getAttribute('data-mileage') : null;
                if (mileage && !mileageInput.value) {
                    mileageInput.value = mileage;
                }
            });
        }

        document.querySelectorAll('[data-date-preset]').forEach(function (button) {
            button.addEventListener('click', function () {
                var form = button.closest('form');
                if (!form) {
                    return;
                }
                var start = form.querySelector('input[name="start"]');
                var end = form.querySelector('input[name="end"]');
                if (!start || !end) {
                    return;
                }
                var now = new Date();
                var today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
                var firstOfMonth = new Date(now.getFullYear(), now.getMonth(), 1);
                var firstOfLastMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1);
                var lastOfLastMonth = new Date(now.getFullYear(), now.getMonth(), 0);
                var monday = new Date(today);
                var day = (today.getDay() + 6) % 7;
                monday.setDate(today.getDate() - day);
                var preset = button.getAttribute('data-date-preset');
                var from = today;
                var to = today;
                if (preset === 'week') {
                    from = monday;
                } else if (preset === 'month') {
                    from = firstOfMonth;
                } else if (preset === 'last-month') {
                    from = firstOfLastMonth;
                    to = lastOfLastMonth;
                }
                var format = function (date) {
                    var month = String(date.getMonth() + 1).padStart(2, '0');
                    var dayOfMonth = String(date.getDate()).padStart(2, '0');
                    return date.getFullYear() + '-' + month + '-' + dayOfMonth;
                };
                start.value = format(from);
                end.value = format(to);
                if (form.requestSubmit) {
                    form.requestSubmit();
                } else {
                    form.submit();
                }
            });
        });

        document.querySelectorAll('[data-item-template]').forEach(function (button) {
            button.addEventListener('click', function () {
                var type = document.getElementById('itemType');
                var description = document.getElementById('itemDescription');
                var quantity = document.getElementById('itemQuantity');
                var price = document.getElementById('itemPrice');
                if (type) {
                    type.value = button.getAttribute('data-type') || type.value;
                }
                if (description) {
                    description.value = button.getAttribute('data-description') || '';
                }
                if (quantity) {
                    quantity.value = button.getAttribute('data-quantity') || '1';
                }
                if (price) {
                    price.value = button.getAttribute('data-price') || '0';
                }
                if (description) {
                    description.focus();
                    description.select();
                }
                document.querySelectorAll('[data-item-template]').forEach(function (other) {
                    other.classList.remove('active');
                });
                button.classList.add('active');
            });
        });

        document.querySelectorAll('[data-signature-pad]').forEach(function (pad) {
            var canvas = pad.querySelector('canvas');
            var input = pad.querySelector('[data-signature-input]');
            var clear = pad.querySelector('[data-signature-clear]');
            if (!canvas || !input) {
                return;
            }
            var ctx = canvas.getContext('2d');
            ctx.lineWidth = 2.2;
            ctx.lineCap = 'round';
            ctx.lineJoin = 'round';
            ctx.strokeStyle = '#111111';
            var drawing = false;
            var point = function (event) {
                var rect = canvas.getBoundingClientRect();
                return {
                    x: (event.clientX - rect.left) * (canvas.width / rect.width),
                    y: (event.clientY - rect.top) * (canvas.height / rect.height)
                };
            };
            var start = function (event) {
                drawing = true;
                var position = point(event);
                ctx.beginPath();
                ctx.moveTo(position.x, position.y);
                if (canvas.setPointerCapture) {
                    canvas.setPointerCapture(event.pointerId);
                }
                event.preventDefault();
            };
            var move = function (event) {
                if (!drawing) {
                    return;
                }
                var position = point(event);
                ctx.lineTo(position.x, position.y);
                ctx.stroke();
                event.preventDefault();
            };
            var end = function () {
                if (!drawing) {
                    return;
                }
                drawing = false;
                input.value = canvas.toDataURL('image/png');
            };
            canvas.addEventListener('pointerdown', start);
            canvas.addEventListener('pointermove', move);
            canvas.addEventListener('pointerup', end);
            canvas.addEventListener('pointerleave', end);
            canvas.addEventListener('pointercancel', end);
            if (clear) {
                clear.addEventListener('click', function () {
                    ctx.clearRect(0, 0, canvas.width, canvas.height);
                    input.value = '';
                });
            }
            if (input.value) {
                var existing = new Image();
                existing.onload = function () {
                    ctx.drawImage(existing, 0, 0, canvas.width, canvas.height);
                };
                existing.src = input.value;
            }
        });

        initPaymentAllocator();

        var kanbanForm = document.getElementById('kanbanMoveForm');
        if (kanbanForm) {
            var allowedMoves = {
                ISLEMDE: ['TAMAMLANDI'],
                TAMAMLANDI: ['TESLIM_EDILDI'],
                TESLIM_EDILDI: []
            };
            var draggedCard = null;
            document.querySelectorAll('.kanban-card[data-order-id]').forEach(function (card) {
                card.addEventListener('dragstart', function (event) {
                    draggedCard = card;
                    card.classList.add('dragging');
                    event.dataTransfer.effectAllowed = 'move';
                    event.dataTransfer.setData('text/plain', card.getAttribute('data-order-id'));
                });
                card.addEventListener('dragend', function () {
                    card.classList.remove('dragging');
                    draggedCard = null;
                    document.querySelectorAll('.kanban-column').forEach(function (column) {
                        column.classList.remove('drag-over');
                    });
                });
            });
            document.querySelectorAll('.kanban-column[data-drop-status]').forEach(function (column) {
                column.addEventListener('dragover', function (event) {
                    if (!draggedCard) {
                        return;
                    }
                    var targets = allowedMoves[draggedCard.getAttribute('data-status')] || [];
                    if (targets.indexOf(column.getAttribute('data-drop-status')) >= 0) {
                        event.preventDefault();
                        column.classList.add('drag-over');
                    }
                });
                column.addEventListener('dragleave', function () {
                    column.classList.remove('drag-over');
                });
                column.addEventListener('drop', function (event) {
                    event.preventDefault();
                    column.classList.remove('drag-over');
                    if (!draggedCard) {
                        return;
                    }
                    var target = column.getAttribute('data-drop-status');
                    var targets = allowedMoves[draggedCard.getAttribute('data-status')] || [];
                    if (targets.indexOf(target) < 0) {
                        return;
                    }
                    document.getElementById('kanbanMoveStatus').value = target;
                    kanbanForm.action = '/work-orders/' + draggedCard.getAttribute('data-order-id') + '/status';
                    kanbanForm.submit();
                });
            });
        }

        var kanbanTabs = Array.prototype.slice.call(document.querySelectorAll('[data-kanban-tab]'));
        if (kanbanTabs.length) {
            var boardMedia = window.matchMedia('(max-width: 767.98px)');
            var boardColumns = Array.prototype.slice.call(
                document.querySelectorAll('.kanban-column[data-drop-status]'));
            var activeKanbanTab = document.querySelector('.kanban-tab.is-active') || kanbanTabs[0];
            var applyKanbanFilter = function () {
                if (!boardMedia.matches) {
                    boardColumns.forEach(function (column) {
                        column.hidden = false;
                    });
                    return;
                }
                var status = activeKanbanTab
                    ? activeKanbanTab.getAttribute('data-kanban-tab') : null;
                boardColumns.forEach(function (column) {
                    column.hidden = column.getAttribute('data-drop-status') !== status;
                });
            };
            kanbanTabs.forEach(function (tab) {
                tab.addEventListener('click', function () {
                    activeKanbanTab = tab;
                    kanbanTabs.forEach(function (other) {
                        other.classList.toggle('is-active', other === tab);
                        other.setAttribute('aria-selected', other === tab ? 'true' : 'false');
                    });
                    applyKanbanFilter();
                });
            });
            applyKanbanFilter();
            if (boardMedia.addEventListener) {
                boardMedia.addEventListener('change', applyKanbanFilter);
            } else if (boardMedia.addListener) {
                boardMedia.addListener(applyKanbanFilter);
            }
        }

        var collapseButtons = Array.prototype.slice.call(
            document.querySelectorAll('[data-kanban-collapse]'));
        if (collapseButtons.length) {
            var collapseKey = 'sd.kanbanCollapsed';
            var collapseState = {};
            try {
                collapseState = JSON.parse(window.localStorage.getItem(collapseKey) || '{}') || {};
            } catch (error) {
                collapseState = {};
            }
            var defaultCollapsed = window.matchMedia('(min-width: 768px)').matches
                ? { TAMAMLANDI: true, TESLIM_EDILDI: true }
                : {};
            var applyCollapse = function (button, collapsed) {
                var status = button.getAttribute('data-kanban-collapse');
                var column = document.querySelector(
                    '.kanban-column[data-drop-status="' + status + '"]');
                if (!column) {
                    return;
                }
                column.classList.toggle('is-collapsed', collapsed);
                button.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
            };
            collapseButtons.forEach(function (button) {
                var status = button.getAttribute('data-kanban-collapse');
                var collapsed = Object.prototype.hasOwnProperty.call(collapseState, status)
                    ? Boolean(collapseState[status])
                    : Boolean(defaultCollapsed[status]);
                applyCollapse(button, collapsed);
                button.addEventListener('click', function () {
                    collapsed = !collapsed;
                    collapseState[status] = collapsed;
                    applyCollapse(button, collapsed);
                    try {
                        window.localStorage.setItem(collapseKey, JSON.stringify(collapseState));
                    } catch (error) {
                        // Depolama kapalıysa tercih yalnızca bu oturumda geçerli olur.
                    }
                });
            });
        }

        if (window.scrollY > 0 && window.scrollY < 120) {
            window.scrollTo(0, 0);
        }

        var palette = document.getElementById('commandPalette');
        var commandInput = document.getElementById('commandInput');
        var commandEmpty = document.getElementById('commandEmpty');
        var commandItems = Array.prototype.slice.call(document.querySelectorAll('[data-command-item]'));
        var commandActive = 0;

        var visibleCommandItems = function () {
            return commandItems.filter(function (item) {
                return !item.hidden;
            });
        };
        var markCommandActive = function (index) {
            var visible = visibleCommandItems();
            if (!visible.length) {
                commandActive = 0;
                return;
            }
            commandActive = Math.max(0, Math.min(index, visible.length - 1));
            visible.forEach(function (item, i) {
                item.classList.toggle('active', i === commandActive);
            });
            visible[commandActive].scrollIntoView({block: 'nearest'});
        };
        var filterCommands = function () {
            var query = (commandInput.value || '').trim().toLocaleLowerCase('tr-TR');
            commandItems.forEach(function (item) {
                var text = (item.textContent + ' ' + (item.getAttribute('data-keywords') || ''))
                    .toLocaleLowerCase('tr-TR');
                item.hidden = query.length > 0 && text.indexOf(query) < 0;
            });
            if (commandEmpty) {
                commandEmpty.hidden = visibleCommandItems().length > 0;
            }
            markCommandActive(0);
        };
        var openPalette = function () {
            if (!palette) {
                return;
            }
            palette.hidden = false;
            document.body.classList.add('command-open');
            commandInput.value = '';
            filterCommands();
            commandInput.focus();
        };
        var closePalette = function () {
            if (!palette) {
                return;
            }
            palette.hidden = true;
            document.body.classList.remove('command-open');
        };
        if (palette) {
            document.querySelectorAll('[data-command-open]').forEach(function (button) {
                button.addEventListener('click', openPalette);
            });
            document.querySelectorAll('[data-command-close]').forEach(function (element) {
                element.addEventListener('click', closePalette);
            });
            commandInput.addEventListener('input', filterCommands);
            commandInput.addEventListener('keydown', function (event) {
                if (event.key === 'ArrowDown') {
                    event.preventDefault();
                    markCommandActive(commandActive + 1);
                } else if (event.key === 'ArrowUp') {
                    event.preventDefault();
                    markCommandActive(commandActive - 1);
                } else if (event.key === 'Enter') {
                    event.preventDefault();
                    var visible = visibleCommandItems();
                    if (visible[commandActive]) {
                        visible[commandActive].click();
                    }
                }
            });
            commandItems.forEach(function (item) {
                item.addEventListener('click', function () {
                    if (item.hasAttribute('data-command-tour')) {
                        closePalette();
                        startTour();
                    } else {
                        closePalette();
                    }
                });
            });
            document.addEventListener('keydown', function (event) {
                if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
                    event.preventDefault();
                    openPalette();
                }
                if (event.key === 'Escape') {
                    closePalette();
                }
            });
        }

        var tourSteps = [
            {url: '/', selector: '.dashboard-kpis', title: 'Günlük kokpit',
                text: 'Açık işler, bugünkü tahsilat, alacak ve randevular tek ekranda. Grafikler son 7 günü ve iş durumlarını gösterir.'},
            {url: '/work-orders/board', selector: '.kanban-board', title: 'Kanban iş panosu',
                text: 'Araçları sürükleyerek veya tek tıkla İşleme Al, Tamamla ve Teslim Et adımlarını ilerletin.'},
            {url: '/work-orders/new', selector: '.quick-start-card', title: 'Plaka ile hızlı iş emri',
                text: 'Plakayı yazın; kayıtlıysa müşteri ve araç otomatik gelir. Kayıtlı değilse müşteri, araç ve iş emri tek adımda oluşur.'},
            {url: '/appointments/week', selector: '.week-grid', title: 'Haftalık randevu planı',
                text: 'Haftanın tamamını görün; güne tıklayarak randevu ekleyin veya mevcut randevuları yönetin.'},
            {url: '/reports/maintenance', selector: '[data-tour="maintenance"]', title: 'Bakım hatırlatmaları',
                text: '180 gün veya 10.000 km geçen araçları görün; müşteriyi arayıp doğrudan randevu oluşturun.'},
            {url: '/', selector: null, title: 'Sunum hazır',
                text: 'Demo hesabıyla tüm ekranları gezebilirsiniz. Sorularınız için hazırım.'}
        ];
        var tourLayer = document.getElementById('tourLayer');
        var tourCounter = document.getElementById('tourCounter');
        var tourTitle = document.getElementById('tourTitle');
        var tourText = document.getElementById('tourText');
        var tourPrev = document.getElementById('tourPrev');
        var tourNext = document.getElementById('tourNext');
        var tourIndex = -1;

        var clearTourHighlight = function () {
            document.querySelectorAll('.tour-highlight').forEach(function (element) {
                element.classList.remove('tour-highlight');
            });
        };
        var closeTour = function () {
            if (tourLayer) {
                tourLayer.hidden = true;
            }
            clearTourHighlight();
            sessionStorage.removeItem('sdTourStep');
            sessionStorage.removeItem('sdTourResume');
            tourIndex = -1;
        };
        var showTourStep = function (index) {
            if (index < 0 || index >= tourSteps.length) {
                closeTour();
                return;
            }
            var step = tourSteps[index];
            sessionStorage.setItem('sdTourStep', String(index));
            if (window.location.pathname !== step.url) {
                sessionStorage.setItem('sdTourResume', '1');
                window.location.href = step.url;
                return;
            }
            if (!tourLayer) {
                return;
            }
            tourLayer.hidden = false;
            clearTourHighlight();
            var target = step.selector ? document.querySelector(step.selector) : null;
            if (target) {
                target.classList.add('tour-highlight');
                target.scrollIntoView({block: 'center', behavior: 'smooth'});
            }
            tourIndex = index;
            tourCounter.textContent = (index + 1) + ' / ' + tourSteps.length;
            tourTitle.textContent = step.title;
            tourText.textContent = step.text;
            tourPrev.style.visibility = index === 0 ? 'hidden' : 'visible';
            tourNext.textContent = index === tourSteps.length - 1 ? 'Bitir' : 'İleri';
        };
        var startTour = function () {
            sessionStorage.setItem('sdTourStep', '0');
            showTourStep(0);
        };
        document.querySelectorAll('[data-tour-start]').forEach(function (button) {
            button.addEventListener('click', startTour);
        });
        if (tourNext) {
            tourNext.addEventListener('click', function () {
                showTourStep(tourIndex + 1);
            });
        }
        if (tourPrev) {
            tourPrev.addEventListener('click', function () {
                showTourStep(tourIndex - 1);
            });
        }
        var tourSkip = document.getElementById('tourSkip');
        if (tourSkip) {
            tourSkip.addEventListener('click', closeTour);
        }
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && tourLayer && !tourLayer.hidden) {
                closeTour();
            }
        });
        var savedTourStep = Number(sessionStorage.getItem('sdTourStep'));
        var resumeTour = sessionStorage.getItem('sdTourResume') === '1';
        sessionStorage.removeItem('sdTourResume');
        if (resumeTour && !Number.isNaN(savedTourStep) && savedTourStep >= 0
                && savedTourStep < tourSteps.length
                && window.location.pathname === tourSteps[savedTourStep].url) {
            showTourStep(savedTourStep);
        } else {
            sessionStorage.removeItem('sdTourStep');
            if (tourLayer) {
                tourLayer.hidden = true;
            }
            clearTourHighlight();
        }

        var firstInvalid = document.querySelector('.is-invalid');
        if (firstInvalid) {
            firstInvalid.focus({preventScroll: false});
        }

        if (window.bootstrap && window.bootstrap.Tooltip) {
            document.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(function (element) {
                new window.bootstrap.Tooltip(element);
            });
        }

        document.querySelectorAll('[data-print]').forEach(function (button) {
            button.addEventListener('click', function () {
                window.print();
            });
        });

        document.addEventListener('keydown', function (event) {
            var target = event.target;
            var tag = target && target.tagName ? target.tagName.toLowerCase() : '';
            var typing = tag === 'input' || tag === 'textarea' || tag === 'select'
                || (target && target.isContentEditable);
            if (event.key === '/' && !typing) {
                var search = document.querySelector('.sd-search input[type="search"]');
                if (search && search.offsetParent !== null) {
                    event.preventDefault();
                    search.focus();
                }
            }
            if (event.altKey && !event.ctrlKey && !event.metaKey) {
                var key = event.key.toLowerCase();
                if (key === 'n') {
                    var newOrder = document.querySelector('[data-shortcut-new-order]');
                    if (newOrder) {
                        event.preventDefault();
                        newOrder.click();
                    }
                }
                if (key === 'b') {
                    var board = document.querySelector('[data-shortcut-board]');
                    if (board) {
                        event.preventDefault();
                        board.click();
                    }
                }
            }
        });

        var recentStorageKey = 'sd.recentOrders';
        var readRecent = function () {
            try {
                var parsed = JSON.parse(window.localStorage.getItem(recentStorageKey) || '[]');
                return Array.isArray(parsed) ? parsed : [];
            } catch (error) {
                return [];
            }
        };
        var recentMarker = document.querySelector('[data-recent-order]');
        if (recentMarker) {
            var entry = {
                url: recentMarker.getAttribute('data-url'),
                no: recentMarker.getAttribute('data-no'),
                customer: recentMarker.getAttribute('data-customer'),
                plate: recentMarker.getAttribute('data-plate')
            };
            if (entry.url) {
                var recent = readRecent().filter(function (item) {
                    return item.url !== entry.url;
                });
                recent.unshift(entry);
                try {
                    window.localStorage.setItem(recentStorageKey, JSON.stringify(recent.slice(0, 6)));
                } catch (error) {
                    // Depolama kapalıysa son işler özelliği sessizce devre dışı kalır.
                }
            }
        }
        var recentCard = document.getElementById('recentOrdersCard');
        var recentList = document.getElementById('recentOrdersList');
        if (recentCard && recentList) {
            var recentItems = readRecent();
            if (recentItems.length > 0) {
                recentCard.hidden = false;
                recentItems.forEach(function (item) {
                    var link = document.createElement('a');
                    link.className = 'list-group-item list-group-item-action px-4 py-3';
                    link.href = item.url;
                    var title = document.createElement('div');
                    title.className = 'fw-semibold';
                    title.textContent = item.no || 'İş emri';
                    var meta = document.createElement('div');
                    meta.className = 'record-meta';
                    meta.textContent = [item.customer, item.plate].filter(Boolean).join(' · ');
                    link.appendChild(title);
                    link.appendChild(meta);
                    recentList.appendChild(link);
                });
            }
        }

        document.querySelectorAll('[data-undo-toast]').forEach(function (toast) {
            var removeToast = function () {
                toast.remove();
            };
            var hideTimer = setTimeout(removeToast, 30000);
            var closeButton = toast.querySelector('[data-undo-close]');
            if (closeButton) {
                closeButton.addEventListener('click', function () {
                    clearTimeout(hideTimer);
                    removeToast();
                });
            }
        });

        var orderNoInput = document.querySelector('[data-order-no]');
        if (orderNoInput) {
            var orderNoCheckUrl = orderNoInput.getAttribute('data-order-no-check-url');
            var orderNoStatus = document.querySelector('[data-order-no-status]');
            var orderNoTimer = null;
            var checkOrderNo = function () {
                var value = orderNoInput.value.trim();
                if (!orderNoStatus) {
                    return;
                }
                if (!value) {
                    orderNoStatus.textContent = '';
                    orderNoStatus.className = 'form-text';
                    return;
                }
                orderNoStatus.textContent = 'Kontrol ediliyor…';
                orderNoStatus.className = 'form-text';
                fetch(orderNoCheckUrl + '?orderNo=' + encodeURIComponent(value), {
                    headers: { Accept: 'application/json' },
                    credentials: 'same-origin'
                }).then(function (response) {
                    if (!response.ok) {
                        throw new Error('check failed');
                    }
                    return response.json();
                }).then(function (data) {
                    orderNoStatus.textContent = data.message || '';
                    orderNoStatus.className = 'form-text ' + (data.available ? 'text-success' : 'text-danger');
                }).catch(function () {
                    orderNoStatus.textContent = '';
                    orderNoStatus.className = 'form-text';
                });
            };
            if (orderNoInput.value.trim()) {
                checkOrderNo();
            }
            orderNoInput.addEventListener('input', function () {
                if (orderNoTimer) {
                    clearTimeout(orderNoTimer);
                }
                orderNoTimer = setTimeout(checkOrderNo, 400);
            });
            orderNoInput.addEventListener('blur', checkOrderNo);
        }

        var quickPlate = document.querySelector('[data-quick-plate]');
        if (quickPlate) {
            var quickLookupUrl = quickPlate.getAttribute('data-lookup-url');
            var quickSuggestUrl = quickPlate.getAttribute('data-suggest-url');
            var quickStatus = document.querySelector('[data-quick-status]');
            var quickMatch = document.querySelector('[data-quick-match]');
            var quickSuggestions = document.querySelector('[data-quick-suggestions]');
            var quickNewCustomer = document.querySelector('[data-quick-new-customer]');
            var quickMileageInput = document.getElementById('quickMileage');
            var quickTimer = null;
            var lastPlate = '';

            var setQuickStatus = function (message) {
                if (quickStatus) {
                    quickStatus.textContent = message || '';
                }
            };
            var showQuickNewCustomer = function (show) {
                if (quickNewCustomer) {
                    quickNewCustomer.hidden = !show;
                }
            };
            var showQuickMatch = function (data) {
                if (!quickMatch) {
                    return;
                }
                quickMatch.hidden = !data;
                if (!data) {
                    return;
                }
                var setText = function (selector, value) {
                    var node = quickMatch.querySelector(selector);
                    if (node) {
                        node.textContent = value || '';
                    }
                };
                setText('[data-match-plate]', data.plate);
                setText('[data-match-customer]', data.customerName);
                setText('[data-match-vehicle]', [data.brand, data.model].filter(Boolean).join(' '));
                var mileage = quickMatch.querySelector('[data-match-mileage]');
                if (mileage) {
                    var mileageValue = Number(data.mileage);
                    if (mileageValue > 0) {
                        mileage.textContent = mileageValue.toLocaleString('tr-TR') + ' km';
                        mileage.hidden = false;
                    } else {
                        mileage.hidden = true;
                    }
                }
            };
            var isLikelyPlate = function (value) {
                var normalized = (value || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
                return /^[0-9]{2}[A-Z]{1,4}[0-9]{2,5}$/.test(normalized);
            };
            var hideQuickSuggestions = function () {
                if (quickSuggestions) {
                    quickSuggestions.hidden = true;
                    quickSuggestions.textContent = '';
                }
            };
            var loadQuickSuggestions = function () {
                if (!quickSuggestions || !quickSuggestUrl) {
                    return;
                }
                var query = quickPlate.value.trim();
                if (query.length < 2) {
                    hideQuickSuggestions();
                    return;
                }
                fetch(quickSuggestUrl + '?q=' + encodeURIComponent(query), {
                    headers: { Accept: 'application/json' },
                    credentials: 'same-origin'
                }).then(function (response) {
                    if (!response.ok) {
                        throw new Error('suggest failed');
                    }
                    return response.json();
                }).then(function (items) {
                    quickSuggestions.textContent = '';
                    if (!Array.isArray(items) || items.length === 0) {
                        quickSuggestions.hidden = true;
                        return;
                    }
                    items.forEach(function (item) {
                        var button = document.createElement('button');
                        button.type = 'button';
                        button.className = 'plate-suggestion';
                        var plate = document.createElement('strong');
                        plate.textContent = item.plate || '';
                        var meta = document.createElement('span');
                        meta.textContent = [item.customerName, item.brand, item.model]
                            .filter(Boolean).join(' · ');
                        button.appendChild(plate);
                        button.appendChild(meta);
                        button.addEventListener('mousedown', function (event) {
                            event.preventDefault();
                            quickPlate.value = item.plate || '';
                            hideQuickSuggestions();
                            lookupQuickPlate(true);
                            quickPlate.focus();
                        });
                        quickSuggestions.appendChild(button);
                    });
                    quickSuggestions.hidden = false;
                }).catch(hideQuickSuggestions);
            };
            var lookupQuickPlate = function (force) {
                var plate = quickPlate.value.trim();
                if (!plate) {
                    lastPlate = '';
                    showQuickMatch(false);
                    showQuickNewCustomer(false);
                    setQuickStatus('');
                    hideQuickSuggestions();
                    return;
                }
                if (!force && !isLikelyPlate(plate)) {
                    showQuickMatch(false);
                    showQuickNewCustomer(false);
                    setQuickStatus('');
                    return;
                }
                hideQuickSuggestions();
                setQuickStatus('Aranıyor…');
                fetch(quickLookupUrl + '?plate=' + encodeURIComponent(plate), {
                    headers: { Accept: 'application/json' },
                    credentials: 'same-origin'
                }).then(function (response) {
                    if (!response.ok) {
                        throw new Error('lookup failed');
                    }
                    return response.json();
                }).then(function (data) {
                    lastPlate = plate;
                    if (data.found) {
                        showQuickMatch(data);
                        showQuickNewCustomer(false);
                        setQuickStatus('Kayıtlı araç bulundu; müşteri bilgileri otomatik gelir.');
                        if (quickMileageInput && !quickMileageInput.value) {
                            var lastMileage = Number(data.mileage);
                            quickMileageInput.placeholder = lastMileage > 0
                                ? 'Son: ' + lastMileage.toLocaleString('tr-TR')
                                : '0';
                        }
                    } else {
                        showQuickMatch(false);
                        showQuickNewCustomer(true);
                        setQuickStatus('Bu plaka kayıtlı değil; yeni müşteri bilgilerini girin.');
                        if (quickMileageInput) {
                            quickMileageInput.placeholder = '0';
                        }
                    }
                }).catch(function () {
                    showQuickMatch(false);
                    showQuickNewCustomer(true);
                    setQuickStatus('Plaka sorgulanamadı; bilgileri elle girebilirsiniz.');
                });
            };

            showQuickNewCustomer(false);
            if (quickPlate.value.trim()) {
                lookupQuickPlate(true);
            }
            quickPlate.addEventListener('input', function () {
                lastPlate = '';
                showQuickMatch(false);
                showQuickNewCustomer(false);
                hideQuickSuggestions();
                setQuickStatus('');
                if (quickTimer) {
                    clearTimeout(quickTimer);
                }
                quickTimer = setTimeout(function () {
                    var value = quickPlate.value.trim();
                    if (!value) {
                        return;
                    }
                    if (isLikelyPlate(value) && value.replace(/\s/g, '').length >= 7) {
                        lookupQuickPlate();
                    } else {
                        loadQuickSuggestions();
                    }
                }, 350);
            });
            quickPlate.addEventListener('blur', function () {
                setTimeout(function () {
                    hideQuickSuggestions();
                    if (quickPlate.value.trim() && quickPlate.value.trim() !== lastPlate
                            && isLikelyPlate(quickPlate.value)) {
                        lookupQuickPlate();
                    }
                }, 150);
            });
            quickPlate.addEventListener('keydown', function (event) {
                if (event.key === 'Enter' && quickSuggestions && !quickSuggestions.hidden) {
                    var first = quickSuggestions.querySelector('.plate-suggestion');
                    if (first) {
                        event.preventDefault();
                        first.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
                    }
                }
            });

            document.querySelectorAll('[data-quick-service]').forEach(function (button) {
                button.addEventListener('click', function () {
                    var input = document.getElementById('quickServiceDescription');
                    if (!input) {
                        return;
                    }
                    input.value = button.getAttribute('data-quick-service');
                    input.focus();
                });
            });
        }
    });
})();
