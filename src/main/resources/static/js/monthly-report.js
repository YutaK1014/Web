'use strict';
const printReport = document.getElementById('print-report');
printReport.hidden = false;
printReport.addEventListener('click', () => window.print());
