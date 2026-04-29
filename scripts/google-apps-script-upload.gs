/**
 * Sample Google Apps Script Web App for RETINA Results Wi‑Fi upload.
 *
 * 1. script.google.com → New project → paste this file.
 * 2. Replace TARGET_FOLDER_ID with your Drive folder id (from URL).
 * 3. Project Settings → Script properties → Add SECRET = shared passphrase (match UPLOAD_SHARED_SECRET in app).
 * 4. Deploy → New deployment → Web app → Execute as: Me → Who has access: Anyone (secret protects writes).
 * 5. Copy Web App URL into UPLOAD_ENDPOINT_URL in app/build.gradle.kts (must be https).
 */
function doPost(e) {
  try {
    var props = PropertiesService.getScriptProperties();
    var expected = props.getProperty('SECRET');
    var data = JSON.parse(e.postData.contents);
    if (!expected || data.secret !== expected) {
      return jsonResponse(false, 'unauthorized');
    }
    var folderId = props.getProperty('TARGET_FOLDER_ID');
    if (!folderId) {
      return jsonResponse(false, 'missing TARGET_FOLDER_ID script property');
    }
    var folder = DriveApp.getFolderById(folderId);
    var txtBlob = Utilities.newBlob(
      Utilities.base64Decode(data.txt_base64),
      'text/plain',
      data.txt_filename
    );
    folder.createFile(txtBlob);
    if (data.csv_base64 && data.csv_filename) {
      var csvBlob = Utilities.newBlob(
        Utilities.base64Decode(data.csv_base64),
        'text/csv',
        data.csv_filename
      );
      folder.createFile(csvBlob);
    }
    return jsonResponse(true, '');
  } catch (err) {
    return jsonResponse(false, String(err));
  }
}

function jsonResponse(ok, error) {
  var payload = { ok: ok };
  if (!ok) payload.error = error;
  return ContentService
    .createTextOutput(JSON.stringify(payload))
    .setMimeType(ContentService.MimeType.JSON);
}
