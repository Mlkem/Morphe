package ca.mlkem.amazon;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Order IDs, dates and manually verified history matches. Excluded from Android backup. */
final class OrderLog {
    private final AtomicFile file;
    final boolean walmart;
    OrderLog(Context context, boolean walmart) {
        this.walmart = walmart;
        file = new AtomicFile(new File(context.getNoBackupFilesDir(), "mlkem-aeroplan-orders-v1.json"));
    }
    boolean valid(String id) { return id != null && id.matches(walmart ? "[0-9]{10,20}" : "[0-9]{3}-[0-9]{7}-[0-9]{7}"); }
    JSONArray read() throws IOException, JSONException {
        try { return new JSONArray(new String(file.readFully(), StandardCharsets.UTF_8)); }
        catch (FileNotFoundException absent) { return new JSONArray(); }
    }
    void save(String id, boolean matched) throws IOException, JSONException {
        if (!valid(id)) throw new IllegalArgumentException("Unexpected order reference");
        JSONArray old = read(), next = new JSONArray();
        JSONObject item = null;
        for (int i=0; i<old.length(); i++) {
            JSONObject record = old.getJSONObject(i);
            if (id.equals(record.getString("id"))) item = record;
        }
        if (item != null && (!matched || item.optBoolean("matched"))) return;
        if (item == null) item = new JSONObject().put("id", id).put("time", System.currentTimeMillis());
        next.put(item.put("matched", matched));
        for (int i=0; i<old.length() && next.length()<100; i++)
            if (!id.equals(old.getJSONObject(i).getString("id"))) next.put(old.getJSONObject(i));
        FileOutputStream out = file.startWrite();
        try { out.write(next.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(out); }
        catch (IOException e) { file.failWrite(out); throw e; }
    }
    void clear() { file.delete(); }
}
