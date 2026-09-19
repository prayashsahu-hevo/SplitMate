/**
 * SplitMate — Google Apps Script backend.
 *
 * Paste this into your Sheet's Apps Script editor (Extensions ▸ Apps Script),
 * then deploy as a Web app (see README). The app POSTs JSON here and this script
 * appends a row to the "Personal" or "Shared" tab, creating them (with headers) if needed.
 */

// Your Google Sheet's ID (the long string in its URL between /d/ and /edit).
// Using openById means this works whether the script is bound to the sheet or standalone.
var SHEET_ID = 'PASTE_YOUR_SHEET_ID_HERE';

var PERSONAL_HEADERS = [
  'Date', 'Time', 'Vendor', 'Amount', 'Reason', 'Category', 'Source', 'Logged At'
];

// One row PER PERSON. "Total Unpaid" is a live formula (col J) = how much THAT person
// still owes across all their Unpaid rows; it updates when a new unpaid row is added
// for them and when you flip one of their rows to Paid.
var SHARED_HEADERS = [
  'Date', 'Time', 'Vendor', 'Reason', 'Category', 'Total Expense',
  'Person', 'Share', 'Status', 'Total Unpaid', 'Source', 'Logged At'
];

function doPost(e) {
  try {
    var data = JSON.parse(e.postData.contents);
    var now = new Date();

    if (data.type === 'personal') {
      var sheet = getOrCreateSheet_('Personal', PERSONAL_HEADERS);
      sheet.appendRow([
        data.date, data.time, data.vendor, Number(data.amount),
        data.reason, data.category, data.source, now
      ]);
    } else if (data.type === 'shared') {
      var sheet = getOrCreateSheet_('Shared', SHARED_HEADERS);
      var people = data.people || [];
      for (var i = 0; i < people.length; i++) {
        var p = people[i];
        sheet.appendRow([
          data.date, data.time, data.vendor, data.reason, data.category,
          Number(data.totalAmount), p.name, Number(p.share), 'Unpaid',
          '', data.source, now
        ]);
        // This person's outstanding: sum of Share (H) where Status (I)="Unpaid"
        // AND Person (G) equals this row's person. Updates when their rows change.
        var r = sheet.getLastRow();
        sheet.getRange(r, 10).setFormula('=SUMIFS(H:H,I:I,"Unpaid",G:G,G' + r + ')');
      }
    } else {
      return json_({ ok: false, error: 'unknown type: ' + data.type });
    }

    return json_({ ok: true });
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  }
}

/** Simple health check when you open the /exec URL in a browser. */
function doGet() {
  return json_({ ok: true, service: 'SplitMate', message: 'POST payment rows here.' });
}

function getOrCreateSheet_(name, headers) {
  var ss = SpreadsheetApp.openById(SHEET_ID);
  var sheet = ss.getSheetByName(name);
  if (!sheet) {
    sheet = ss.insertSheet(name);
  }
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(headers);
    sheet.getRange(1, 1, 1, headers.length).setFontWeight('bold');
    sheet.setFrozenRows(1);
  }
  return sheet;
}

function json_(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
