const supabase = require('../utils/supabase');
const fs = require('fs');
const path = require('path');
const mime = require('mime-types');

const storageBucket = process.env.SUPABASE_BUCKET_NAME || process.env.SUPABASE_BUCKET || 'files';

exports.uploadFile = async (req, res) => {
  try {
    if (!req.file) {
      console.warn('[files] Skipped upload: no file provided');
      return res.status(400).json({ error: 'No file provided' });
    }

    const { folder, deviceId, storagePath: requestedStoragePath, localPath } = req.body;
    const effectiveDeviceId = deviceId || folder || 'unknown-device';
    const fileName = req.file.originalname;
    const timestamp = Date.now();
    const storedFileName = `${timestamp}_${fileName}`;
    const storagePath = sanitizeStoragePath(requestedStoragePath)
      || `${sanitizePathSegment(effectiveDeviceId)}/${storedFileName}`;

    let mimeType = req.file.mimetype;
    if (!mimeType || mimeType === 'application/octet-stream') {
      mimeType = mime.lookup(fileName) || 'application/octet-stream';
    }

    console.log('[files] Detected file:', {
      fileName,
      localPath,
      storagePath,
      deviceId: effectiveDeviceId,
      bytes: req.file.size,
      mimeType
    });

    console.log('[files] Upload start:', { storageBucket, storagePath, bytes: req.file.size, mimeType });
    const { data: uploadData, error: uploadError } = await supabase.storage
      .from(storageBucket)
      .upload(storagePath, req.file.buffer, {
        contentType: mimeType,
        upsert: true
      });

    if (uploadError) {
      console.error('[files] Upload failure: Supabase Storage error', uploadError);
      return res.status(500).json({ error: 'Failed to upload to cloud storage', detail: uploadError.message });
    }

    const { data: { publicUrl } } = supabase.storage
      .from(storageBucket)
      .getPublicUrl(storagePath);

    const { data: dbData, error: dbError } = await supabase
      .from('files_metadata')
      .insert([{
        device_id: effectiveDeviceId,
        file_name: fileName,
        file_url: publicUrl,
        storage_path: storagePath,
        file_size: req.file.size,
        file_type: getExtension(fileName),
        uploaded_at: new Date().toISOString()
      }])
      .select();

    if (dbError) {
      console.error('[files] Supabase DB metadata error:', dbError);
    }

    const localDir = path.join(__dirname, '../uploads');
    if (!fs.existsSync(localDir)) fs.mkdirSync(localDir, { recursive: true });
    fs.writeFileSync(path.join(localDir, storedFileName), req.file.buffer);

    console.log('[files] Upload success:', { fileName, storagePath, publicUrl, uploadData });
    res.status(201).json({
      message: 'File synchronized successfully to cloud',
      file: dbData ? dbData[0] : { fileName, fileUrl: publicUrl, storagePath, file_size: req.file.size }
    });
  } catch (err) {
    console.error('[files] Upload exception:', err);
    res.status(500).json({ error: 'Internal server error during synchronization', detail: err.message });
  }
};

exports.getFilesByDevice = async (req, res) => {
  const { deviceId } = req.params;

  try {
    const { data, error } = await supabase
      .from('files_metadata')
      .select('*')
      .eq('device_id', deviceId)
      .order('uploaded_at', { ascending: false });

    if (error) throw error;
    res.status(200).json(data);
  } catch (err) {
    console.error('[files] Get files exception:', err);
    res.status(500).json({ error: 'Error fetching files for device from cloud', detail: err.message });
  }
};

function sanitizeStoragePath(value) {
  if (!value || typeof value !== 'string') return '';
  return value
    .replace(/\\/g, '/')
    .replace(/\.\./g, '_')
    .replace(/^\/+/, '')
    .split('/')
    .map(sanitizePathSegment)
    .filter(Boolean)
    .join('/');
}

function sanitizePathSegment(value) {
  return String(value || '')
    .trim()
    .replace(/[^a-zA-Z0-9._ -]/g, '_')
    .replace(/^_+|_+$/g, '');
}

function getExtension(fileName) {
  const ext = path.extname(fileName || '').replace('.', '').toLowerCase();
  return ext || 'unknown';
}
