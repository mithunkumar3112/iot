const supabase = require('../utils/supabase');
const fs = require('fs');
const path = require('path');

exports.uploadFile = async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: 'No file provided' });
    }

    const { folder, deviceId } = req.body;
    const effectiveDeviceId = deviceId || folder || 'unknown-device';

    const fileName = req.file.originalname;
    const timestamp = Date.now();
    const storedFileName = `${timestamp}_${fileName}`;
    const storagePath = `${effectiveDeviceId}/${storedFileName}`;

    // 1. Upload to Supabase Storage
    const { data: uploadData, error: uploadError } = await supabase.storage
      .from('monitor-files')
      .upload(storagePath, req.file.buffer, {
        contentType: req.file.mimetype,
        upsert: true
      });

    if (uploadError) {
      console.error('❌ Supabase Storage Upload Error:', uploadError);
      return res.status(500).json({ error: 'Failed to upload to cloud storage' });
    }

    // 2. Get Public URL
    const { data: { publicUrl } } = supabase.storage
      .from('monitor-files')
      .getPublicUrl(storagePath);

    // 3. Save Metadata to Supabase DB
    const { data: dbData, error: dbError } = await supabase
      .from('files_metadata')
      .insert([{
        device_id: effectiveDeviceId,
        file_name: fileName,
        file_url: publicUrl,
        storage_path: storagePath,
        uploaded_at: new Date().toISOString()
      }])
      .select();

    if (dbError) {
      console.error('❌ Supabase DB Error:', dbError);
      // We don't return error here because the file is already uploaded
    }

    // Optional: Save locally as backup (as per original logic)
    const localDir = path.join(__dirname, '../uploads');
    if (!fs.existsSync(localDir)) fs.mkdirSync(localDir);
    const localPath = path.join(localDir, storedFileName);
    fs.writeFileSync(localPath, req.file.buffer);

    res.status(201).json({
      message: 'File synchronized successfully to cloud',
      file: dbData ? dbData[0] : { fileName, fileUrl: publicUrl }
    });

  } catch (err) {
    console.error('Upload Controller Error:', err);
    res.status(500).json({ error: 'Internal server error during synchronization' });
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
    console.error('Get Files Error:', err);
    res.status(500).json({ error: 'Error fetching files for device from cloud' });
  }
};
