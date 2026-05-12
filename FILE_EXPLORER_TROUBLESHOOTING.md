# File Explorer Fix - Troubleshooting Guide

## Quick Diagnosis

Run this to understand your current state:

```bash
# 1. Check service status
curl https://your-app.onrender.com/files/debug/status

# 2. Get count of files
curl https://your-app.onrender.com/files/all | jq 'length'

# 3. Get files from specific device
curl https://your-app.onrender.com/files/device/your-device-id

# 4. Check Supabase directly
curl https://your-app.onrender.com/files/supabase

# 5. Check local database files
curl https://your-app.onrender.com/files/debug/local
```

---

## Problem: "No files found" in File Explorer

### Diagnosis Flow

```
Is Supabase enabled in /files/debug/status?
├─ NO → Go to: Issue #1 "Supabase Not Configured"
├─ YES → Does /files/all return any files?
    ├─ NO → Go to: Issue #2 "Files Not Being Uploaded"
    ├─ YES → Does /files/device/{deviceId} return files?
        ├─ NO → Go to: Issue #3 "Device ID Mismatch"
        ├─ YES → Is File Explorer loading the right API?
            ├─ Check browser Network tab (F12 → Network)
            └─ Go to: Issue #4 "Frontend Not Loading Correct API"
```

---

## Issue #1: Supabase Not Configured

### Symptoms
```json
{
  "supabaseEnabled": false,
  "localDatabaseFileCount": 0,
  "timestamp": 1715502600000
}
```

### Root Causes

**Cause A: Environment variables not set**
```bash
# Check Render dashboard
Settings → Environment

Missing:
- SUPABASE_URL
- SUPABASE_SERVICE_ROLE_KEY or SUPABASE_KEY
- SUPABASE_BUCKET_NAME
```

**Cause B: Supabase URL malformed**
```bash
# Correct format:
SUPABASE_URL=https://oavizsbcurtnsekuwebj.supabase.co

# Wrong formats to avoid:
SUPABASE_URL=https://oavizsbcurtnsekuwebj.supabase.co/  # Trailing slash
SUPABASE_URL=oavizsbcurtnsekuwebj.supabase.co           # Missing https://
```

**Cause C: Service role key wrong**
```bash
# Verify in Supabase dashboard
Settings → API → Service Role Key

# Should start with eyJh (JWT encoding)
# Should be 200+ characters
```

### Solution

1. Go to Render → Your Service → Environment
2. Add/update these:
   ```
   SUPABASE_URL=https://oavizsbcurtnsekuwebj.supabase.co
   SUPABASE_SERVICE_ROLE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
   SUPABASE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
   SUPABASE_BUCKET_NAME=monitor-files
   SUPABASE_ENABLED=true
   ```
3. Save and wait for redeploy (2-3 minutes)
4. Test: `curl your-app/files/debug/status`

---

## Issue #2: Files Not Being Uploaded

### Symptoms
```json
{
  "supabaseEnabled": true,
  "localDatabaseFileCount": 0
}
```

And `/files/all` returns `[]` even after uploading.

### Check Server Logs
```bash
# Go to Render Logs tab
# Search for: "FILE UPLOAD"

# You should see:
FILE UPLOAD START: deviceId=xxx, filename=yyy, size=123 bytes

# If not there: Files aren't reaching the backend
```

### Root Causes

**Cause A: Agent not uploading**
- Check agent logs for upload errors
- Verify agent has network connection to backend
- Test upload manually:
  ```bash
  curl -X POST http://localhost:5000/upload \
    -F "file=@test.pdf" \
    -F "deviceId=test-device"
  ```

**Cause B: Backend upload endpoint broken**
- Check Render logs for:
  ```
  ❌ FILE UPLOAD FAILED: 
  ❌ SUPABASE UPLOAD FAILED:
  ```
- Verify `/upload` endpoint exists

**Cause C: Supabase bucket upload fails**
- Check logs for:
  ```
  Supabase upload failed: status=403
  Supabase upload failed: status=401
  ```
- **403**: Bucket permissions issue
- **401**: Authentication failed (bad API key)

### Solution

1. **Test upload endpoint directly**:
   ```bash
   curl -X POST http://your-app.onrender.com/upload \
     -F "file=@/path/to/file.pdf" \
     -F "deviceId=test-device"
   ```

2. **Check response**:
   ```json
   // Success:
   {
     "message": "File uploaded to Supabase successfully",
     "fileUrl": "https://...",
     "metadataId": "abc123"
   }
   
   // Error:
   {
     "error": "Failed to save file: ..."
   }
   ```

3. **Check Render logs** for full error:
   ```
   FILE UPLOAD FAILED: IO error: Permission denied
   FILE UPLOAD FAILED: Unexpected error: ...
   ```

4. **Verify Supabase bucket**:
   - Go to Supabase → Storage
   - Bucket `monitor-files` exists?
   - Is it set to public?
   - Try uploading file directly to Supabase UI

---

## Issue #3: Device ID Mismatch

### Symptoms
```bash
# These succeed:
curl /files/all          # Returns files
curl /files/supabase     # Returns files

# But this fails:
curl /files/device/my-device  # Returns []

# And File Explorer shows "No files found"
```

### Root Cause
Frontend filter uses different device ID than uploaded files.

**Example**:
- Files uploaded with: `deviceId=laptop-001`
- Frontend selects: `deviceId=LAPTOP-001` (different case)
- Or frontend sends: `deviceId=unknown-device`

### Solution

1. **Check what device ID is being used**:
   ```bash
   # In browser console (F12 → Console):
   console.log(shell.getDevice());  # Shows selected device
   ```

2. **Check what device IDs are in database**:
   ```bash
   curl /files/all | jq '.[].device_id'
   # Returns: ["laptop-001", "desktop-02", "phone-01"]
   ```

3. **Match them**:
   - If browser shows `undefined`: Device not selected, click dropdown
   - If device shows `laptop-001` but API shows `unknown-device`: 
     - Agent not sending correct deviceId
     - Check agent code

4. **Fix device selection**:
   - Open File Explorer
   - Click device dropdown at top
   - Select device
   - Page should reload with device-specific files

---

## Issue #4: Frontend Not Loading Correct API

### Symptoms
- Logs show files in database
- `/files/device/xxx` returns files
- But File Explorer page shows "No files found"

### Debug Steps

1. **Open browser DevTools** (F12)
2. **Go to Network tab**
3. **Reload File Explorer page**
4. **Look for requests to `/files/`**

You should see:
```
GET /files/device/laptop-001  ← Correct
GET /files/all                 ← Fallback (also correct)

NOT:
GET /files/list               ← Old broken endpoint
```

5. **Check response**:
   - Click the request
   - Go to Response tab
   - Should see JSON array of files
   - Not empty `[]`

### Root Causes

**Cause A: Browser cache**
- Old HTML/JS cached
- Solution: Hard refresh (Ctrl+Shift+R or Cmd+Shift+R)

**Cause B: Wrong endpoint still being called**
- Check HTML file has correct code
- Should have: `fetch('/files/device/' + selectedDevice)`
- Not: `fetch('/files/list')`

**Cause C: API returns empty but shouldn't**
- Device ID being sent doesn't match database
- See Issue #3 above

### Solution

1. **Hard refresh browser**:
   - Windows: Ctrl+Shift+R
   - Mac: Cmd+Shift+R

2. **Clear browser cache**:
   - F12 → Application → Clear storage → Clear all

3. **Check browser console** for errors:
   ```javascript
   // Should see logs like:
   console.log('Loaded', allFiles.length, 'files total')
   // If shows 0: API returned empty array
   
   // Should NOT see:
   console.error('File loading error:')
   ```

4. **Verify code change deployed**:
   ```bash
   # Render logs should show code was redeployed
   # Check timestamp of deployment
   
   # Or test directly:
   curl -s your-app.onrender.com/files-explorer.html | grep "/files/device"
   # Should find the string "/files/device"
   ```

---

## Issue #5: Supabase Bucket Doesn't Exist

### Symptoms
```
Logs show:
SUPABASE UPLOAD FAILED: status=404
```

Or:
```json
{
  "error": "Supabase storage is disabled or misconfigured",
  "details": "bucket not found"
}
```

### Solution

1. **Go to Supabase Dashboard**
2. **Click Storage** in left sidebar
3. **Look for `monitor-files` bucket**
4. **If not there**: Click "New bucket"
   - Name: `monitor-files`
   - Public: YES
   - Click Create

5. **Verify it works**:
   ```bash
   # Test upload to bucket directly
   curl -X POST https://oavizsbcurtnsekuwebj.supabase.co/storage/v1/object/monitor-files/test.txt \
     -H "Authorization: Bearer $SUPABASE_SERVICE_ROLE_KEY" \
     -H "Content-Type: text/plain" \
     -d "test content"
   ```

---

## Issue #6: Files Upload But Metadata Not Saved

### Symptoms
```bash
# File appears in Supabase Storage
# But doesn't appear in API response

Logs show:
FILE UPLOAD START: ✅
SUPABASE STORAGE SUCCESS: ✅
SUPABASE METADATA FAILED: ❌ (this line appears)
LOCAL DATABASE SAVED: ✅

# But /files/all still returns []
```

### Root Cause
`file_metadata` table doesn't exist or schema is wrong.

### Solution

1. **Create the table in Supabase**:
   ```sql
   -- Go to Supabase SQL Editor
   CREATE TABLE IF NOT EXISTS files_metadata (
       id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
       device_id TEXT NOT NULL,
       file_name TEXT NOT NULL,
       file_url TEXT NOT NULL,
       storage_path TEXT NOT NULL,
       file_size BIGINT,
       file_type VARCHAR(50),
       uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
   );
   
   CREATE INDEX IF NOT EXISTS idx_files_metadata_device_id ON files_metadata(device_id);
   ```

2. **Verify it was created**:
   ```sql
   SELECT * FROM files_metadata LIMIT 1;
   ```

3. **Re-upload a file**:
   ```bash
   curl -X POST http://your-app/upload \
     -F "file=@test.pdf" \
     -F "deviceId=test-device"
   ```

4. **Check logs**:
   ```
   SUPABASE METADATA SUCCESS: ✅
   ```

---

## Issue #7: API Returns 500 Server Error

### Symptoms
```bash
curl /files/device/test-device
# Returns: 500 Internal Server Error
```

### Solution

1. **Check Render logs** for error:
   ```
   Exception in AgentFileUploadController
   NullPointerException at...
   SQLException...
   ```

2. **Common causes**:

   **Cause A: Database connection error**
   - Check H2 database is initialized
   - Logs should show: `H2 console available`

   **Cause B: Missing dependencies**
   - Supabase library might not have loaded
   - Check build succeeded: no "BUILD FAILURE"

   **Cause C: Exception in code**
   - Check full stack trace in logs
   - File path or database issues

3. **Fix**:
   - Check Render build logs for compile errors
   - If build failed, deployment won't work
   - Try manual redeploy from Render dashboard

---

## Quick Verification Checklist

```
□ Supabase credentials set on Render
□ Bucket 'monitor-files' exists and is public
□ Service role key is valid (test with curl)
□ file_metadata table exists in Supabase
□ Agent can upload files (test with curl)
□ /files/all returns data
□ /files/device/{deviceId} returns device-specific data
□ Browser hard-refreshed (Ctrl+Shift+R)
□ File Explorer page loads without errors
□ Selected device from dropdown
□ Files appear in table
□ Preview/Download buttons work
```

---

## Contact Support if...

- Supabase bucket keeps getting deleted
- Service role key doesn't work (verify with official docs)
- Render environment variables not applying
- Consistent 403/401 errors from Supabase
- Build keeps failing (check Render logs)

Provide:
1. Error message from logs
2. Response from `/files/debug/status`
3. Contents of `/files/all` endpoint
4. Browser console errors (F12 → Console)
5. Supabase table structure: `\d files_metadata`
