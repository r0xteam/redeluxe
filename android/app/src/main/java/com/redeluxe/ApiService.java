package com.redeluxe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ApiService {
    private static final String BASE_URL = "http://85.234.110.4:3000/api"; // для эмулятора
    private Context context;
    private String token;

    public interface ApiCallback<T> {
        void onSuccess(T result);
        void onError(String error);
    }

    public ApiService(Context context) {
        this.context = context;
        loadToken();
    }

    private void loadToken() {
        SharedPreferences prefs = context.getSharedPreferences("redeluxe", Context.MODE_PRIVATE);
        token = prefs.getString("token", "");
    }

    private void saveToken(String token) {
        this.token = token;
        SharedPreferences prefs = context.getSharedPreferences("redeluxe", Context.MODE_PRIVATE);
        prefs.edit().putString("token", token).apply();
    }

    // авторизация
    public void login(String email, String password, ApiCallback<String> callback) {
        executeRequest("POST", "/login", createAuthJson(email, password, null), callback, false);
    }

    public void register(String email, String password, String username, ApiCallback<String> callback) {
        executeRequest("POST", "/register", createAuthJson(email, password, username), callback, false);
    }

    // заметки
    public void getNotes(ApiCallback<List<Note>> callback) {
        executeRequest("GET", "/notes", null, new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    JSONObject json = new JSONObject(result);
                    List<Note> notes = parseNotes(json.getJSONArray("notes"));
                    callback.onSuccess(notes);
                } catch (JSONException e) {
                    callback.onError("ошибка парсинга");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        }, true);
    }

    public void createNote(Note note, ApiCallback<Note> callback) {
        executeRequest("POST", "/notes", noteToJson(note), new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    callback.onSuccess(parseNote(new JSONObject(result)));
                } catch (JSONException e) {
                    callback.onError("ошибка парсинга");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        }, true);
    }

    public void updateNote(int noteId, Note note, ApiCallback<Note> callback) {
        executeRequest("PUT", "/notes/" + noteId, noteToJson(note), new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    callback.onSuccess(parseNote(new JSONObject(result)));
                } catch (JSONException e) {
                    callback.onError("ошибка парсинга");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        }, true);
    }

    public void deleteNote(int noteId, ApiCallback<String> callback) {
        executeRequest("DELETE", "/notes/" + noteId, null, callback, true);
    }

    // поиск
    public void searchNotes(String query, ApiCallback<List<Note>> callback) {
        executeRequest("GET", "/notes/search?q=" + query, null, new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    JSONObject json = new JSONObject(result);
                    List<Note> notes = parseNotes(json.getJSONArray("notes"));
                    callback.onSuccess(notes);
                } catch (JSONException e) {
                    callback.onError("ошибка парсинга");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        }, true);
    }

    // категории
    public void getCategories(ApiCallback<List<Category>> callback) {
        executeRequest("GET", "/categories", null, new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    JSONObject json = new JSONObject(result);
                    List<Category> categories = parseCategories(json.getJSONArray("categories"));
                    callback.onSuccess(categories);
                } catch (JSONException e) {
                    callback.onError("ошибка парсинга");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        }, true);
    }

    public void createCategory(Category category, ApiCallback<Category> callback) {
        executeRequest("POST", "/categories", categoryToJson(category), new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    callback.onSuccess(parseCategory(new JSONObject(result)));
                } catch (JSONException e) {
                    callback.onError("ошибка парсинга");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        }, true);
    }

    // теги
    public void getTags(ApiCallback<List<Tag>> callback) {
        executeRequest("GET", "/tags", null, new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    JSONObject json = new JSONObject(result);
                    List<Tag> tags = parseTags(json.getJSONArray("tags"));
                    callback.onSuccess(tags);
                } catch (JSONException e) {
                    callback.onError("ошибка парсинга");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        }, true);
    }

    public void createTag(Tag tag, ApiCallback<Tag> callback) {
        executeRequest("POST", "/tags", tagToJson(tag), new ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    callback.onSuccess(parseTag(new JSONObject(result)));
                } catch (JSONException e) {
                    callback.onError("ошибка парсинга");
                }
            }

            @Override
            public void onError(String error) {
                callback.onError(error);
            }
        }, true);
    }

    // статистика
    public void getStats(ApiCallback<String> callback) {
        executeRequest("GET", "/stats", null, callback, true);
    }

    // утилиты
    private String createAuthJson(String email, String password, String username) {
        try {
            JSONObject json = new JSONObject();
            json.put("email", email);
            json.put("password", password);
            if (username != null) {
                json.put("username", username);
            }
            return json.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    private String noteToJson(Note note) {
        try {
                JSONObject json = new JSONObject();
                json.put("title", note.getTitle());
                json.put("content", note.getContent());
                json.put("color", note.getColor());
            json.put("is_pinned", note.isPinned());
            json.put("is_archived", note.isArchived());
            if (note.getCategoryId() != null) {
                json.put("category_id", note.getCategoryId());
            }
            return json.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    private String categoryToJson(Category category) {
        try {
            JSONObject json = new JSONObject();
            json.put("name", category.getName());
            json.put("color", category.getColor());
            json.put("icon", category.getIcon());
            return json.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    private String tagToJson(Tag tag) {
        try {
            JSONObject json = new JSONObject();
            json.put("name", tag.getName());
            json.put("color", tag.getColor());
            return json.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    private List<Note> parseNotes(JSONArray array) throws JSONException {
        List<Note> notes = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            notes.add(parseNote(array.getJSONObject(i)));
        }
        return notes;
    }

    private Note parseNote(JSONObject json) throws JSONException {
        Note note = new Note();
        note.setId(json.getInt("id"));
        note.setTitle(json.getString("title"));
        note.setContent(json.getString("content"));
        note.setColor(json.getString("color"));
        note.setPinned(json.optBoolean("is_pinned", false));
        note.setArchived(json.optBoolean("is_archived", false));
        note.setCreatedAt(json.getString("created_at"));
        note.setUpdatedAt(json.getString("updated_at"));
        
        if (json.has("category") && !json.isNull("category")) {
            note.setCategory(parseCategory(json.getJSONObject("category")));
        }
        
        return note;
    }

    private List<Category> parseCategories(JSONArray array) throws JSONException {
        List<Category> categories = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            categories.add(parseCategory(array.getJSONObject(i)));
        }
        return categories;
    }

    private Category parseCategory(JSONObject json) throws JSONException {
        Category category = new Category();
        category.setId(json.getInt("id"));
        category.setName(json.getString("name"));
        category.setColor(json.getString("color"));
        category.setIcon(json.getString("icon"));
        return category;
    }

    private List<Tag> parseTags(JSONArray array) throws JSONException {
        List<Tag> tags = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            tags.add(parseTag(array.getJSONObject(i)));
        }
        return tags;
    }

    private Tag parseTag(JSONObject json) throws JSONException {
        Tag tag = new Tag();
        tag.setId(json.getInt("id"));
        tag.setName(json.getString("name"));
        tag.setColor(json.getString("color"));
        return tag;
    }

    public void executeRequest(String method, String endpoint, String data, ApiCallback<String> callback, boolean needAuth) {
        new Thread(() -> {
            try {
                URL url = new URL(BASE_URL + endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                
                conn.setRequestMethod(method);
                conn.setRequestProperty("Content-Type", "application/json");
                
                if (needAuth && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }

                if (data != null && (method.equals("POST") || method.equals("PUT"))) {
                    conn.setDoOutput(true);
                    DataOutputStream out = new DataOutputStream(conn.getOutputStream());
                    out.writeBytes(data);
                    out.flush();
                    out.close();
                }

                int responseCode = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                    responseCode >= 200 && responseCode < 300 ? 
                    conn.getInputStream() : conn.getErrorStream()
                ));

                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                new Handler(Looper.getMainLooper()).post(() -> {
                    if (responseCode >= 200 && responseCode < 300) {
                        // сохраняем токен из ответа
                        if (!needAuth) {
                            try {
                                JSONObject json = new JSONObject(response.toString());
                                if (json.has("token")) {
                                    saveToken(json.getString("token"));
                                    callback.onSuccess(json.getString("token"));
                                    return;
                                }
                            } catch (JSONException e) {
                                // игнорируем
                            }
                        }
                        callback.onSuccess(response.toString());
                    } else {
                        try {
                            JSONObject errorJson = new JSONObject(response.toString());
                            callback.onError(errorJson.optString("error", "неизвестная ошибка"));
                        } catch (JSONException e) {
                            callback.onError("ошибка сети");
                        }
                    }
                });

            } catch (IOException e) {
                new Handler(Looper.getMainLooper()).post(() -> 
                    callback.onError("ошибка сети: " + e.getMessage())
                );
            }
        }).start();
    }
} 