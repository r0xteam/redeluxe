package com.redeluxe;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class NoteEditActivity extends AppCompatActivity {
    private EditText titleInput;
    private EditText contentInput;
    private ApiService apiService;
    private int noteId = -1;
    private String currentColor = "#7c3aed";
    private String[] colors = {"#7c3aed", "#8b5cf6", "#a855f7", "#c084fc", "#ddd6fe", "#e879f9", "#f472b6", "#fb7185"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_edit);

        initViews();
        loadNoteData();
        apiService = new ApiService(this);
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        titleInput = findViewById(R.id.titleInput);
        contentInput = findViewById(R.id.contentInput);
    }

    private void loadNoteData() {
        noteId = getIntent().getIntExtra("note_id", -1);
        if (noteId != -1) {
            // редактирование существующей заметки
            getSupportActionBar().setTitle("редактировать");
            titleInput.setText(getIntent().getStringExtra("note_title"));
            contentInput.setText(getIntent().getStringExtra("note_content"));
            currentColor = getIntent().getStringExtra("note_color");
        } else {
            // создание новой заметки
            getSupportActionBar().setTitle("новая заметка");
        }
        updateBackgroundColor();
    }

    private void updateBackgroundColor() {
        try {
            getWindow().getDecorView().setBackgroundColor(Color.parseColor(currentColor + "40"));
        } catch (Exception e) {
            getWindow().getDecorView().setBackgroundColor(Color.parseColor("#7c3aed40"));
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.note_edit_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_save:
                saveNote();
                return true;
            case R.id.action_color:
                showColorPicker();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void saveNote() {
        String title = titleInput.getText().toString().trim();
        String content = contentInput.getText().toString().trim();

        if (content.isEmpty()) {
            Toast.makeText(this, "заметка не может быть пустой", Toast.LENGTH_SHORT).show();
            return;
        }

        Note note = new Note(title, content, currentColor);

        if (noteId == -1) {
            // создание новой заметки
            apiService.createNote(note, new ApiService.ApiCallback<Note>() {
                @Override
                public void onSuccess(Note result) {
                    runOnUiThread(() -> {
                        Toast.makeText(NoteEditActivity.this, "заметка создана", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(NoteEditActivity.this, "ошибка создания: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            // обновление существующей заметки
            apiService.updateNote(noteId, note, new ApiService.ApiCallback<Note>() {
                @Override
                public void onSuccess(Note result) {
                    runOnUiThread(() -> {
                        Toast.makeText(NoteEditActivity.this, "заметка обновлена", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(NoteEditActivity.this, "ошибка обновления: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        }
    }

    private void showColorPicker() {
        // простой выбор цвета - циклический переход по цветам
        int currentIndex = 0;
        for (int i = 0; i < colors.length; i++) {
            if (colors[i].equals(currentColor)) {
                currentIndex = i;
                break;
            }
        }
        currentIndex = (currentIndex + 1) % colors.length;
        currentColor = colors[currentIndex];
        updateBackgroundColor();
        Toast.makeText(this, "цвет изменен", Toast.LENGTH_SHORT).show();
    }
} 