package com.redeluxe;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.chip.Chip;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private RecyclerView notesRecyclerView;
    private NotesAdapter notesAdapter;
    private ApiService apiService;
    private List<Note> allNotes = new ArrayList<>();
    private List<Note> filteredNotes = new ArrayList<>();
    
    private EditText searchInput;
    private TextView notesCountText;
    private ImageButton menuButton;
    private LinearLayout emptyState;
    
    private ExtendedFloatingActionButton addNoteFab;
    private LinearLayout fabGraphContainer, fabCanvasContainer, fabDailyContainer, fabTemplateContainer;
    private FloatingActionButton fabGraph, fabCanvas, fabDaily, fabTemplate;
    
    private Chip chipAll, chipPinned, chipArchived, chipCategories;
    private String currentFilter = "all";
    private boolean isFabMenuOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupRecyclerView();
        setupListeners();

        apiService = new ApiService(this);
        loadNotes();
    }

    private void initViews() {
        notesRecyclerView = findViewById(R.id.notesRecyclerView);
        searchInput = findViewById(R.id.searchInput);
        notesCountText = findViewById(R.id.notesCountText);
        menuButton = findViewById(R.id.menuButton);
        emptyState = findViewById(R.id.emptyState);
        
        // FAB menu
        addNoteFab = findViewById(R.id.addNoteFab);
        fabGraphContainer = findViewById(R.id.fabGraphContainer);
        fabCanvasContainer = findViewById(R.id.fabCanvasContainer);
        fabDailyContainer = findViewById(R.id.fabDailyContainer);
        fabTemplateContainer = findViewById(R.id.fabTemplateContainer);
        
        fabGraph = findViewById(R.id.fabGraph);
        fabCanvas = findViewById(R.id.fabCanvas);
        fabDaily = findViewById(R.id.fabDaily);
        fabTemplate = findViewById(R.id.fabTemplate);
        
        // Filter chips
        chipAll = findViewById(R.id.chipAll);
        chipPinned = findViewById(R.id.chipPinned);
        chipArchived = findViewById(R.id.chipArchived);
        chipCategories = findViewById(R.id.chipCategories);
    }

    private void setupRecyclerView() {
        notesAdapter = new NotesAdapter(filteredNotes, new NotesAdapter.OnNoteActionListener() {
            @Override
            public void onNoteClick(Note note) {
                Intent intent = new Intent(MainActivity.this, NoteEditActivity.class);
                intent.putExtra("note_id", note.getId());
                intent.putExtra("note_title", note.getTitle());
                intent.putExtra("note_content", note.getContent());
                intent.putExtra("note_color", note.getColor());
                startActivity(intent);
            }

            @Override
            public void onNoteArchive(Note note) {
                archiveNote(note);
            }

            @Override
            public void onNoteDelete(Note note) {
                showDeleteConfirmation(note);
            }

            @Override
            public void onNotePin(Note note) {
                pinNote(note);
            }

            @Override
            public void onNoteLongClick(Note note) {
                showNoteContextMenu(note);
            }
        });

        notesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        notesRecyclerView.setAdapter(notesAdapter);
    }

    private void setupListeners() {
        // Main FAB
        addNoteFab.setOnClickListener(v -> {
            if (isFabMenuOpen) {
                closeFabMenu();
            } else {
                openFabMenu();
            }
        });

        // FAB menu items
        fabGraph.setOnClickListener(v -> {
            closeFabMenu();
            Intent intent = new Intent(MainActivity.this, GraphActivity.class);
            startActivity(intent);
        });

        fabCanvas.setOnClickListener(v -> {
            closeFabMenu();
            Intent intent = new Intent(MainActivity.this, CanvasActivity.class);
            startActivity(intent);
        });

        fabDaily.setOnClickListener(v -> {
            closeFabMenu();
            createDailyNote();
        });

        fabTemplate.setOnClickListener(v -> {
            closeFabMenu();
            showTemplatesDialog();
        });

        // Menu button
        menuButton.setOnClickListener(v -> showMainMenu());

        // Filter chips
        chipAll.setOnClickListener(v -> applyFilter("all"));
        chipPinned.setOnClickListener(v -> applyFilter("pinned"));
        chipArchived.setOnClickListener(v -> applyFilter("archived"));
        chipCategories.setOnClickListener(v -> applyFilter("categories"));

        // Search
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            String query = searchInput.getText().toString().trim();
            if (!query.isEmpty()) {
                searchNotes(query);
            } else {
                applyFilter(currentFilter);
            }
            return true;
        });
    }

    private void openFabMenu() {
        isFabMenuOpen = true;
        addNoteFab.setText("×");
        
        // Анимируем появление FAB'ов
        animateFabContainer(fabTemplateContainer, 0, 100);
        animateFabContainer(fabDailyContainer, 50, 150);
        animateFabContainer(fabCanvasContainer, 100, 200);
        animateFabContainer(fabGraphContainer, 150, 250);
    }

    private void closeFabMenu() {
        isFabMenuOpen = false;
        addNoteFab.setText("новая заметка");
        
        // Анимируем скрытие FAB'ов
        animateFabContainer(fabGraphContainer, 0, 0);
        animateFabContainer(fabCanvasContainer, 50, 0);
        animateFabContainer(fabDailyContainer, 100, 0);
        animateFabContainer(fabTemplateContainer, 150, 0);
    }

    private void animateFabContainer(LinearLayout container, int delay, int targetAlpha) {
        if (targetAlpha > 0) {
            container.setVisibility(View.VISIBLE);
            
            ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(container, "alpha", 0f, 1f);
            alphaAnimator.setDuration(200);
            alphaAnimator.setStartDelay(delay);
            alphaAnimator.start();
            
            ObjectAnimator translateAnimator = ObjectAnimator.ofFloat(container, "translationY", 50f, 0f);
            translateAnimator.setDuration(300);
            translateAnimator.setStartDelay(delay);
            translateAnimator.setInterpolator(new OvershootInterpolator());
            translateAnimator.start();
        } else {
            ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(container, "alpha", 1f, 0f);
            alphaAnimator.setDuration(150);
            alphaAnimator.setStartDelay(delay);
            alphaAnimator.addListener(new Animator.AnimatorListener() {
            @Override
                public void onAnimationEnd(Animator animation) {
                    container.setVisibility(View.GONE);
                }
                
                @Override public void onAnimationStart(Animator animation) {}
                @Override public void onAnimationCancel(Animator animation) {}
                @Override public void onAnimationRepeat(Animator animation) {}
            });
            alphaAnimator.start();
        }
    }

    private void showNoteContextMenu(Note note) {
        String[] options = {
            note.isPinned() ? "📌 открепить" : "📌 закрепить",
            note.isArchived() ? "📦 разархивировать" : "📦 архивировать",
            "📝 редактировать",
            "🎨 изменить цвет",
            "🔗 создать связь",
            "🗑️ удалить"
        };

        new AlertDialog.Builder(this)
            .setTitle("◢ " + note.getTitle())
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: pinNote(note); break;
                    case 1: archiveNote(note); break;
                    case 2: editNote(note); break;
                    case 3: showColorPicker(note); break;
                    case 4: showLinkDialog(note); break;
                    case 5: showDeleteConfirmation(note); break;
                }
            })
            .setNegativeButton("отмена", null)
            .show();
    }

    private void showMainMenu() {
        String[] options = {
            "📊 статистика",
            "💾 создать backup",
            "⚙️ настройки",
            "ℹ️ о приложении"
        };

        new AlertDialog.Builder(this)
            .setTitle("◢ redeluxe")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: showStatsDialog(); break;
                    case 1: createBackup(); break;
                    case 2: showSettings(); break;
                    case 3: showAbout(); break;
                }
            })
            .setNegativeButton("отмена", null)
            .show();
    }

    private void showColorPicker(Note note) {
        String[] colors = {"#00ffff", "#ff0080", "#00ff41", "#ff8000", "#8000ff", "#ff1744"};
        String[] colorNames = {"◇ cyan", "◇ pink", "◇ green", "◇ orange", "◇ purple", "◇ red"};

        new AlertDialog.Builder(this)
            .setTitle("выберите цвет")
            .setItems(colorNames, (dialog, which) -> {
                note.setColor(colors[which]);
                updateNote(note);
            })
            .setNegativeButton("отмена", null)
            .show();
    }

    private void showLinkDialog(Note note) {
        // Простая заглушка для создания связей
        Toast.makeText(this, "функция создания связей в разработке", Toast.LENGTH_SHORT).show();
    }

    private void showDeleteConfirmation(Note note) {
        new AlertDialog.Builder(this)
            .setTitle("удалить заметку?")
            .setMessage("◢ " + note.getTitle() + "\n\nэто действие нельзя отменить")
            .setPositiveButton("удалить", (dialog, which) -> deleteNote(note))
            .setNegativeButton("отмена", null)
            .show();
    }

    private void editNote(Note note) {
        Intent intent = new Intent(MainActivity.this, NoteEditActivity.class);
        intent.putExtra("note_id", note.getId());
        intent.putExtra("note_title", note.getTitle());
        intent.putExtra("note_content", note.getContent());
        intent.putExtra("note_color", note.getColor());
        startActivity(intent);
    }

    private void createBackup() {
        Toast.makeText(this, "создание backup...", Toast.LENGTH_SHORT).show();
        // API вызов для создания backup
    }

    private void showSettings() {
        Toast.makeText(this, "настройки в разработке", Toast.LENGTH_SHORT).show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
            .setTitle("◢ redeluxe")
            .setMessage("версия: 2.0\nразработано: c0rex64.dev\n\nсистема управления знаниями в стиле obsidian")
            .setPositiveButton("ok", null)
            .show();
    }

    private void loadNotes() {
        apiService.getNotes(new ApiService.ApiCallback<List<Note>>() {
            @Override
            public void onSuccess(List<Note> notes) {
                allNotes.clear();
                allNotes.addAll(notes);
                applyFilter(currentFilter);
                updateEmptyState();
                updateNotesCount();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this, "ошибка загрузки: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateNotesCount() {
        notesCountText.setText(String.valueOf(filteredNotes.size()));
    }

    private void applyFilter(String filter) {
        currentFilter = filter;
        filteredNotes.clear();

        switch (filter) {
            case "all":
                filteredNotes.addAll(allNotes);
                break;
            case "pinned":
                for (Note note : allNotes) {
                    if (note.isPinned()) {
                        filteredNotes.add(note);
                    }
                }
                break;
            case "archived":
                for (Note note : allNotes) {
                    if (note.isArchived()) {
                        filteredNotes.add(note);
                    }
                }
                break;
            case "categories":
                for (Note note : allNotes) {
                    if (note.getCategory() != null) {
                        filteredNotes.add(note);
                    }
                }
                break;
        }

        notesAdapter.notifyDataSetChanged();
        updateEmptyState();
        updateNotesCount();
    }

    private void searchNotes(String query) {
        apiService.searchNotes(query, new ApiService.ApiCallback<List<Note>>() {
            @Override
            public void onSuccess(List<Note> notes) {
                filteredNotes.clear();
                filteredNotes.addAll(notes);
                notesAdapter.notifyDataSetChanged();
                updateEmptyState();
                updateNotesCount();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this, "ошибка поиска: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void archiveNote(Note note) {
        note.setArchived(!note.isArchived());
        updateNote(note);
    }

    private void pinNote(Note note) {
        note.setPinned(!note.isPinned());
        updateNote(note);
    }

    private void updateNote(Note note) {
        apiService.updateNote(note.getId(), note, new ApiService.ApiCallback<Note>() {
            @Override
            public void onSuccess(Note updatedNote) {
                for (int i = 0; i < allNotes.size(); i++) {
                    if (allNotes.get(i).getId() == updatedNote.getId()) {
                        allNotes.set(i, updatedNote);
                        break;
                    }
                }
                applyFilter(currentFilter);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this, "ошибка обновления: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteNote(Note note) {
        apiService.deleteNote(note.getId(), new ApiService.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                allNotes.remove(note);
                applyFilter(currentFilter);
                    Toast.makeText(MainActivity.this, "заметка удалена", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String error) {
                    Toast.makeText(MainActivity.this, "ошибка удаления: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateEmptyState() {
        if (filteredNotes.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            notesRecyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            notesRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotes();
    }

    @Override
    public void onBackPressed() {
        if (isFabMenuOpen) {
            closeFabMenu();
        } else {
            super.onBackPressed();
        }
    }

    // новые obsidian функции
    private void createDailyNote() {
        String today = java.time.LocalDate.now().toString();
        
        Note dailyNote = new Note();
        dailyNote.setTitle("Daily Note - " + today);
        dailyNote.setContent("# " + today + "\n\n## задачи\n- [ ] \n\n## заметки\n\n## рефлексия\n");
        dailyNote.setColor("#00ffff");

        apiService.createNote(dailyNote, new ApiService.ApiCallback<Note>() {
            @Override
            public void onSuccess(Note note) {
                Toast.makeText(MainActivity.this, "daily note создана", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(MainActivity.this, NoteEditActivity.class);
                intent.putExtra("note_id", note.getId());
                intent.putExtra("note_title", note.getTitle());
                intent.putExtra("note_content", note.getContent());
                intent.putExtra("note_color", note.getColor());
                startActivity(intent);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this, "ошибка создания daily note: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showTemplatesDialog() {
        String[] templates = {
            "📋 meeting notes",
            "💡 идея проекта", 
            "📚 конспект лекции",
            "✅ todo список",
            "📖 заметки к книге",
            "🎯 цели на неделю"
        };

        String[] templateContents = {
            "# Meeting Notes\n\n**Дата:** \n**Участники:** \n\n## Повестка\n- \n\n## Решения\n- \n\n## Действия\n- [ ] ",
            "# Идея проекта\n\n## Описание\n\n## Цели\n\n## Ресурсы\n\n## Следующие шаги\n- [ ] ",
            "# Конспект лекции\n\n**Тема:** \n**Дата:** \n\n## Основные тезисы\n\n## Вопросы\n\n## Выводы\n",
            "# TODO\n\n## Сегодня\n- [ ] \n\n## На неделе\n- [ ] \n\n## Важное\n- [ ] ",
            "# Заметки к книге\n\n**Название:** \n**Автор:** \n\n## Основные идеи\n\n## Цитаты\n\n## Выводы\n",
            "# Цели на неделю\n\n## Работа\n- [ ] \n\n## Личное\n- [ ] \n\n## Развитие\n- [ ] "
        };

        new AlertDialog.Builder(this)
            .setTitle("◢ выберите шаблон")
            .setItems(templates, (dialog, which) -> {
                Note templateNote = new Note();
                templateNote.setTitle(templates[which].split(" ", 2)[1]);
                templateNote.setContent(templateContents[which]);
                templateNote.setColor("#ff0080");

                apiService.createNote(templateNote, new ApiService.ApiCallback<Note>() {
                    @Override
                    public void onSuccess(Note note) {
                        Toast.makeText(MainActivity.this, "заметка из шаблона создана", Toast.LENGTH_SHORT).show();
                        loadNotes();
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(MainActivity.this, "ошибка создания заметки: " + error, Toast.LENGTH_SHORT).show();
                    }
                });
            })
            .setNegativeButton("отмена", null)
            .show();
    }

    private void showStatsDialog() {
        apiService.getStats(new ApiService.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    org.json.JSONObject stats = new org.json.JSONObject(result);
                    
                    String message = String.format(
                        "◢ статистика\n\n" +
                        "📝 заметок: %d\n" +
                        "📂 категорий: %d\n" +
                        "🏷️ тегов: %d\n\n" +
                        "📅 сегодня: %d\n" +
                        "📊 за неделю: %d\n" +
                        "📈 за месяц: %d",
                        stats.getInt("total_notes"),
                        stats.getInt("total_categories"), 
                        stats.getInt("total_tags"),
                        stats.getInt("notes_today"),
                        stats.getInt("notes_this_week"),
                        stats.getInt("notes_this_month")
                    );

                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("📊 redeluxe stats")
                        .setMessage(message)
                        .setPositiveButton("ok", null)
                        .show();

                } catch (org.json.JSONException e) {
                    Toast.makeText(MainActivity.this, "ошибка парсинга статистики", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this, "ошибка загрузки статистики: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
} 