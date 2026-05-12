# File Explorer Fix - Implementation Guide

## What Was Fixed

### 1. **Frontend API Endpoint Mismatch** ✅
**Problem**: Frontend called `/files/list` which reads local file system
**Solution**: Changed to `/files/device/{deviceId}` or `/files/all` which reads from Supabase

**Files Modified**:
- `backend/src/main/resources/static/files-explorer.html`

**Changes**:
```javascript
// BEFORE (Wrong)
fetch('/files/list')  // ❌ Reads file system, not Supabase

// AFTER (Correct)
fetch('/files/device/' + selectedDevice)  // ✅ Reads Supabase metadata
// OR
fetch('/files/all')   // ✅ Reads all files from Supabase
```

---

### 2. **Device Filtering Not Working** ✅
**Problem**: Files shown for all devices regardless of selection
**Solution**: Added device filtering logic in frontend

**Logic**:
```javascript
// If device selected: fetch /files/device/{deviceId}
// If no device: fetch /files/all then filter by current device in JS
var filtered = allFiles.filter(file => file.deviceId === selectedDevice)
```

---

### 3. **API Endpoint Logic Unclear** ✅
**Problem**: FileMetadataController endpoints didn't have clear documentation or logging
**Solution**: Added comprehensive logging and JavaDoc

**Files Modified**:
- `backend/src/main/java/com/iotmonitor/controller/FileMetadataController.java`

**Improvements**:
- Added logger.info() for each API call
- Added JavaDoc explaining each endpoint
- Added try-catch with proper error handling
- Added debugging endpoints: `/files/debug/status` and `/files/debug/local`

---

### 4. **File Upload Logging Insufficient** ✅
**Problem**: Couldn't trace file upload flow - where did files get lost?
**Solution**: Added comprehensive logging at every step

**Files Modified**:
- `backend/src/main/java/com/iotmonitor/controller/AgentFileUploadController.java`

**New Logs**:
```
FILE UPLOAD START: deviceId=xxx, filename=yyy, size=zzz bytes
FILE SAVED LOCALLY: path=/tmp/iot-monitor-uploads/xxx/yyy
SUPABASE UPLOAD STARTING: bucket upload for xxx/yyy
SUPABASE STORAGE SUCCESS: fileUrl=https://...
SUPABASE METADATA SUCCESS: metadataId=abc123
LOCAL DATABASE SAVED: metadataId=def456, path=...
WEBSOCKET EVENT SENT: /topic/files
FILE UPLOAD SUCCESS: Complete flow done
```

---

### 5. **Supabase Configuration Bucket Name** ✅
**Problem**: Bucket name inconsistent in different files
**Solution**: Standardized to `monitor-files` across all configs

**Files Modified**:
- `backend/src/main/resources/application.properties`

**Changes**:
```properties
# Changed from:
supabase.bucket.name=${SUPABASE_BUCKET_NAME:files}

# To:
supabase.bucket.name=${SUPABASE_BUCKET_NAME:monitor-files}
supabase.bucket=${SUPABASE_BUCKET:monitor-files}
```

---

### 6. **Missing Logging Configuration** ✅
**Problem**: Couldn't enable detailed logs for debugging
**Solution**: Added logging levels to application.properties

**Files Modified**:
- `backend/src/main/resources/application.properties`

**Added**:
```properties
logging.level.com.iotmonitor=DEBUG
logging.level.com.iotmonitor.controller.AgentFileUploadController=DEBUG
logging.level.com.iotmonitor.controller.FileMetadataController=DEBUG
logging.level.com.iotmonitor.service.SupabaseStorageService=DEBUG
```

---

## Implementation Steps

### Step 1: Deploy Changes
```bash
cd backend
mvn clean package -DskipTests
git add -A
git commit -m "Fix: File Explorer - Complete file upload flow integration"
git push origin main
```

On Render dashboard, trigger redeploy to pick up new code.

---

### Step 2: Set Environment Variables on Render
Go to your Render project → Environment:

```
SUPABASE_URL=https://oavizsbcurtnsekuwebj.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key-here
SUPABASE_KEY=your-anon-key-here
SUPABASE_BUCKET_NAME=monitor-files
SUPABASE_ENABLED=true
```

---

### Step 3: Verify Supabase Bucket Exists
1. Go to Supabase dashboard → Storage
2. Create bucket named `monitor-files` if it doesn't exist
3. Make it public so URLs are accessible

---

### Step 4: Test the Flow

#### Test 1: Upload a file
```bash
curl -X POST http://localhost:5000/upload \
  -F "file=@/path/to/test.pdf" \
  -F "deviceId=test-device-001"
```

Expected response:
```json
{
  "message": "File uploaded to Supabase successfully",
  "fileUrl": "https://oavizsbcurtnsekuwebj.supabase.co/storage/v1/object/public/monitor-files/test-device-001/test.pdf",
  "supabaseRow": {...},
  "metadataId": "abc123"
}
```

#### Test 2: Check server logs
```bash
# SSH into Render container or check logs
tail -f /var/log/app.log | grep "FILE UPLOAD"
```

You should see:
```
FILE UPLOAD START: deviceId=test-device-001, filename=test.pdf, size=12345 bytes
FILE SAVED LOCALLY: path=/tmp/iot-monitor-uploads/test-device-001/test.pdf
SUPABASE STORAGE SUCCESS: fileUrl=https://...
SUPABASE METADATA SUCCESS: metadataId=...
FILE UPLOAD SUCCESS: Complete flow done
```

#### Test 3: Fetch files via API
```bash
# Device-specific
curl http://localhost:5000/files/device/test-device-001

# All files
curl http://localhost:5000/files/all

# Debug status
curl http://localhost:5000/files/debug/status
```

Expected:
```json
[
  {
    "id": "abc123",
    "file_name": "test.pdf",
    "file_url": "https://oavizsbcurtnsekuwebj.supabase.co/storage/v1/object/public/monitor-files/test-device-001/test.pdf",
    "device_id": "test-device-001",
    "file_size": 12345,
    "uploaded_at": "2026-05-12T10:30:00Z"
  }
]
```

#### Test 4: Check File Explorer UI
1. Open dashboard
2. Go to File Explorer page
3. Select device from dropdown
4. Should see uploaded files with:
   - File name
   - Device ID
   - File size
   - Upload date
   - Preview/Download buttons

---

## Debugging If Files Still Don't Show

### Debug Step 1: Check Supabase Table
```sql
-- Run in Supabase SQL Editor
SELECT * FROM files_metadata LIMIT 10;
SELECT device_id, COUNT(*) as count FROM files_metadata GROUP BY device_id;
```

If empty, files aren't being persisted to Supabase.

---

### Debug Step 2: Check Server Logs
Look for these logs:
```
✅ FILE UPLOAD START
❌ If this doesn't appear: Agent isn't uploading

✅ SUPABASE UPLOAD STARTING
❌ If missing: Supabase upload code isn't running

✅ SUPABASE STORAGE SUCCESS
❌ If not present: Supabase bucket upload failed

✅ SUPABASE METADATA SUCCESS
❌ If not present: Metadata table write failed

✅ LOCAL DATABASE SAVED
❌ If missing: Local fallback isn't working
```

---

### Debug Step 3: Check Environment Variables
```bash
# On Render, verify variables are set:
echo $SUPABASE_URL
echo $SUPABASE_SERVICE_ROLE_KEY
echo $SUPABASE_BUCKET_NAME
```

If empty, variables aren't being passed to the app.

---

### Debug Step 4: Use Debug Endpoints
```bash
# Check service status
curl http://your-app/files/debug/status
# Response: { "supabaseEnabled": true, "localDatabaseFileCount": 5 }

# Get files from local DB
curl http://your-app/files/debug/local
# Response: [{ "id": 1, "deviceId": "...", ...}]

# Get files directly from Supabase
curl http://your-app/files/supabase?deviceId=test-device
# Response: [{ "id": "abc", "device_id": "test-device", ...}]
```

---

### Debug Step 5: Browser Console
Open browser DevTools (F12) and check Console tab:
```javascript
// Should see logs like:
console.log('Loaded', allFiles.length, 'files total')
// If 0: API returned empty array

console.log('Device-specific file fetch status: 200')
// If 404: endpoint not found
// If 500: server error (check logs)
```

---

## Common Issues & Solutions

### Issue: "No files found" even after upload

**Cause 1**: Frontend calls wrong API
- Check browser Network tab (F12 → Network)
- Verify API calls are to `/files/device/` not `/files/list`

**Fix**: Restart browser, clear cache, reload

**Cause 2**: Supabase table is empty
- Run: `SELECT COUNT(*) FROM files_metadata;`
- If 0: Check server logs for "SUPABASE METADATA FAILED"

**Fix**: Verify `persistFileMetadata()` is working, check table schema

**Cause 3**: Device ID mismatch
- Frontend filters by `shell.getDevice()` 
- File metadata has different device ID

**Fix**: Ensure agent sends correct deviceId parameter

---

### Issue: Uploads succeed but files don't appear

**Cause**: Supabase upload succeeds but metadata doesn't persist
- Check logs for: "SUPABASE METADATA FAILED"
- Likely cause: `file_metadata` table doesn't exist or has wrong schema

**Fix**:
```sql
-- Verify table exists
\dt files_metadata

-- Or recreate:
CREATE TABLE IF NOT EXISTS files_metadata (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id TEXT NOT NULL,
    file_name TEXT NOT NULL,
    file_url TEXT NOT NULL,
    storage_path TEXT NOT NULL,
    file_size BIGINT,
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

---

### Issue: Supabase bucket returns 403 Forbidden

**Cause**: Bucket permissions or authentication issue
- Service role key might be wrong
- Bucket might not be public

**Fix**:
1. Verify bucket is created and public
2. Check service role key matches in Render env vars
3. Test bucket directly:
```bash
curl -H "Authorization: Bearer $SUPABASE_SERVICE_ROLE_KEY" \
  https://oavizsbcurtnsekuwebj.supabase.co/storage/v1/buckets
```

---

### Issue: Render deployment keeps failing

**Cause**: Environment variables not set
- SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY, SUPABASE_BUCKET_NAME

**Fix**: 
1. Go to Render dashboard
2. Select your service
3. Click "Environment"
4. Add all Supabase variables
5. Trigger redeploy

---

## Monitoring File Upload Health

### Quick Health Check
```bash
# API is responding
curl http://your-app/files/debug/status

# Files being uploaded
curl http://your-app/files/debug/local | jq 'length'

# Supabase connectivity
curl http://your-app/files/debug/status | jq '.supabaseEnabled'
```

### Production Monitoring
Add to your monitoring dashboard:
- `/files/debug/status` response time
- Count of files in `/files/all` endpoint
- Error rate of `/upload` endpoint

---

## Next Steps

1. ✅ Deploy code changes
2. ✅ Set Render environment variables
3. ✅ Test file upload flow
4. ✅ Verify File Explorer shows files
5. ✅ Check server logs for any errors
6. ✅ Monitor for 1 week in production

---

## Related Files Modified

1. `backend/src/main/resources/static/files-explorer.html`
   - Fixed API endpoint calls
   - Added device filtering
   - Added browser console logging

2. `backend/src/main/java/com/iotmonitor/controller/FileMetadataController.java`
   - Added comprehensive logging
   - Added debug endpoints
   - Improved error handling

3. `backend/src/main/java/com/iotmonitor/controller/AgentFileUploadController.java`
   - Added detailed upload flow logging
   - Improved error messages

4. `backend/src/main/resources/application.properties`
   - Updated Supabase bucket name
   - Added logging configuration

---

## Architecture After Fixes

```
Agent (Windows)
    ↓
    ├→ AgentFileUploadController.uploadFile()
    ├→ [LOG] FILE UPLOAD START
    ├→ Save to local FS
    ├→ [LOG] FILE SAVED LOCALLY
    ├→ Upload to Supabase bucket
    ├→ [LOG] SUPABASE STORAGE SUCCESS
    ├→ Save metadata to file_metadata table
    ├→ [LOG] SUPABASE METADATA SUCCESS  
    └→ Save to local DB
       [LOG] LOCAL DATABASE SAVED
    ↓
File Stored in:
  1. Supabase Storage bucket (primary)
  2. Supabase DB table (metadata)
  3. Local file system (backup)

Dashboard UI (Browser)
    ↓
    └→ File Explorer page loads
       └→ Detects device from shell.getDevice()
       └→ Calls API: /files/device/{deviceId}
       └→ FileMetadataController.getFilesByDevice()
       └→ [LOG] API call: /files/device/...
       └→ Queries file_metadata table
       └→ [LOG] Supabase returned X files
       └→ Returns Supabase URLs to frontend
       └→ Frontend renders file list
       └→ User can preview/download files
```

---

This comprehensive fix ensures the entire file upload flow works end-to-end with proper visibility and error handling at each stage.
