/**
 * SplitMate — Google Apps Script backend.
 *
 * Paste this into your Sheet's Apps Script editor (Extensions ▸ Apps Script),
 * then deploy as a Web app (see README). The app POSTs JSON here and this script
 * appends a row to the "Personal" or "Shared" tab, creating them (with headers) if needed.
 */

// Your Google Sheet's ID (the long string in its URL between /d/ and /edit).
// Using openById means this works whether the script is bound to the sheet or standalone.
var SHEET_ID = 'YOUR_SHEET_ID';

var PERSONAL_HEADERS = [
  'Date', 'Time', 'Vendor', 'Amount', 'Reason', 'Category', 'Source', 'Logged At'
];

var SHARED_HEADERS = [
  'Date', 'Time', 'Vendor', 'Total Amount', 'Category', 'Reason',
  'Split Count', 'Per-Person Share', 'Your Share',
  'Contacts Messaged', 'To Chase Manually', 'Source', 'Logged At'
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
      sheet.appendRow([
        data.date, data.time, data.vendor, Number(data.totalAmount),
        data.category, data.reason, Number(data.numPeople),
        Number(data.perPersonShare), Number(data.yourShare),
        data.contacts, data.unreached, data.source, now
      ]);
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
