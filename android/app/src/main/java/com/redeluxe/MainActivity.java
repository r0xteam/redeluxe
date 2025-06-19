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
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

// модель для canvas
class CanvasItem {
    private int id;
    private String name;
    private String createdAt;
    private String updatedAt;
    
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}

// модель для графиков
class GraphItem {
    private int id;
    private String name;
    private String layout;
    private String createdAt;
    private String updatedAt;
    
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLayout() { return layout; }
    public void setLayout(String layout) { this.layout = layout; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}

// общий элемент списка
class ListItem {
    public static final int TYPE_NOTE = 0;
    public static final int TYPE_CANVAS = 1;
    public static final int TYPE_GRAPH = 2;
    
    private int type;
    private Note note;
    private CanvasItem canvas;
    private GraphItem graph;
    
    public ListItem(Note note) {
        this.type = TYPE_NOTE;
        this.note = note;
    }
    
    public ListItem(CanvasItem canvas) {
        this.type = TYPE_CANVAS;
        this.canvas = canvas;
    }
    
    public ListItem(GraphItem graph) {
        this.type = TYPE_GRAPH;
        this.graph = graph;
    }
    
    public int getType() { return type; }
    public Note getNote() { return note; }
    public CanvasItem getCanvas() { return canvas; }
    public GraphItem getGraph() { return graph; }
}

public class MainActivity extends AppCompatActivity {
    private RecyclerView notesRecyclerView;
    private NotesAdapter notesAdapter;
    private ApiService apiService;
    private List<Note> allNotes = new ArrayList<>();
    private List<CanvasItem> allCanvases = new ArrayList<>();
    private List<GraphItem> allGraphs = new ArrayList<>();
    private List<ListItem> filteredItems = new ArrayList<>();
    
    private EditText searchInput;
    private TextView notesCountText;
    private ImageButton menuButton;
    private LinearLayout emptyState;
    
    private ExtendedFloatingActionButton addNoteFab;
    private LinearLayout fabGraphContainer, fabCanvasContainer, fabDailyContainer, fabTemplateContainer;
    private FloatingActionButton fabGraph, fabCanvas, fabDaily, fabTemplate;
    
    private Chip chipAll, chipPinned, chipArchived, chipCategories, chipCanvas, chipGraphs;
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
        loadAllData();
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
        
        // новые фильтры для canvas и графиков
        chipCanvas = findViewById(R.id.chipCanvas);
        chipGraphs = findViewById(R.id.chipGraphs);
    }

    private void setupRecyclerView() {
        notesAdapter = new NotesAdapter(filteredItems, new NotesAdapter.OnNoteActionListener() {
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
            
            @Override
            public void onCanvasClick(CanvasItem canvas) {
                Intent intent = new Intent(MainActivity.this, CanvasActivity.class);
                intent.putExtra("canvas_id", canvas.getId());
                intent.putExtra("canvas_name", canvas.getName());
                startActivity(intent);
            }
            
            @Override
            public void onGraphClick(GraphItem graph) {
                Intent intent = new Intent(MainActivity.this, GraphActivity.class);
                intent.putExtra("graph_id", graph.getId());
                intent.putExtra("graph_name", graph.getName());
                startActivity(intent);
            }
            
            @Override
            public void onCanvasDelete(CanvasItem canvas) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Удалить Canvas?")
                        .setMessage("Вы уверены, что хотите удалить \"" + canvas.getName() + "\"?")
                        .setPositiveButton("Удалить", (dialog, which) -> deleteCanvas(canvas.getId()))
                        .setNegativeButton("Отмена", null)
                        .show();
            }
            
            @Override
            public void onGraphDelete(GraphItem graph) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Удалить Граф?")
                        .setMessage("Вы уверены, что хотите удалить \"" + graph.getName() + "\"?")
                        .setPositiveButton("Удалить", (dialog, which) -> deleteGraph(graph.getId()))
                        .setNegativeButton("Отмена", null)
                        .show();
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
            showNewGraphDialog();
        });

        fabCanvas.setOnClickListener(v -> {
            closeFabMenu();
            showNewCanvasDialog();
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
        chipCanvas.setOnClickListener(v -> applyFilter("canvas"));
        chipGraphs.setOnClickListener(v -> applyFilter("graphs"));

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

    private void loadAllData() {
        loadNotes();
        loadCanvases();
        loadGraphs();
    }

    private void loadCanvases() {
        apiService.getCanvases(new ApiService.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    JSONObject json = new JSONObject(result);
                    JSONArray canvasesArray = json.getJSONArray("canvases");
                    
                    allCanvases.clear();
                    for (int i = 0; i < canvasesArray.length(); i++) {
                        JSONObject canvasJson = canvasesArray.getJSONObject(i);
                        CanvasItem canvas = new CanvasItem();
                        canvas.setId(canvasJson.getInt("id"));
                        canvas.setName(canvasJson.getString("name"));
                        canvas.setCreatedAt(canvasJson.getString("created_at"));
                        canvas.setUpdatedAt(canvasJson.getString("updated_at"));
                        allCanvases.add(canvas);
                    }
                    
                    applyFilter(currentFilter);
                } catch (JSONException e) {
                    // игнорируем ошибку загрузки canvas
                }
            }

            @Override
            public void onError(String error) {
                // игнорируем ошибку загрузки canvas
            }
        });
    }

    private void loadGraphs() {
        apiService.getGraphStates(new ApiService.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    JSONObject json = new JSONObject(result);
                    JSONArray statesArray = json.getJSONArray("states");
                    
                    allGraphs.clear();
                    for (int i = 0; i < statesArray.length(); i++) {
                        JSONObject stateJson = statesArray.getJSONObject(i);
                        
                        GraphItem graph = new GraphItem();
                        graph.setId(stateJson.getInt("id"));
                        graph.setName(stateJson.getString("name"));
                        graph.setLayout(stateJson.optString("layout", "force"));
                        graph.setCreatedAt(stateJson.getString("created_at"));
                        graph.setUpdatedAt(stateJson.getString("updated_at"));
                        allGraphs.add(graph);
                    }
                    
                    applyFilter(currentFilter);
                } catch (JSONException e) {
                    // игнорируем ошибку загрузки графов
                }
            }

            @Override
            public void onError(String error) {
                // игнорируем ошибку загрузки графов
            }
        });
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
        notesCountText.setText(String.valueOf(filteredItems.size()));
    }

    private void applyFilter(String filter) {
        currentFilter = filter;
        filteredItems.clear();

        switch (filter) {
            case "all":
                for (Note note : allNotes) {
                    filteredItems.add(new ListItem(note));
                }
                for (CanvasItem canvas : allCanvases) {
                    filteredItems.add(new ListItem(canvas));
                }
                for (GraphItem graph : allGraphs) {
                    filteredItems.add(new ListItem(graph));
                }
                break;
            case "pinned":
                for (Note note : allNotes) {
                    if (note.isPinned()) {
                        filteredItems.add(new ListItem(note));
                    }
                }
                break;
            case "archived":
                for (Note note : allNotes) {
                    if (note.isArchived()) {
                        filteredItems.add(new ListItem(note));
                    }
                }
                break;
            case "categories":
                for (Note note : allNotes) {
                    if (note.getCategory() != null) {
                        filteredItems.add(new ListItem(note));
                    }
                }
                break;
            case "canvas":
                for (CanvasItem canvas : allCanvases) {
                    filteredItems.add(new ListItem(canvas));
                }
                break;
            case "graphs":
                for (GraphItem graph : allGraphs) {
                    filteredItems.add(new ListItem(graph));
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
                filteredItems.clear();
                for (Note note : notes) {
                    filteredItems.add(new ListItem(note));
                }
                
                // поиск по canvas
                String lowerQuery = query.toLowerCase();
                for (CanvasItem canvas : allCanvases) {
                    if (canvas.getName().toLowerCase().contains(lowerQuery)) {
                        filteredItems.add(new ListItem(canvas));
                    }
                }
                
                // поиск по графикам
                for (GraphItem graph : allGraphs) {
                    if (graph.getName().toLowerCase().contains(lowerQuery)) {
                        filteredItems.add(new ListItem(graph));
                    }
                }
                
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
        if (filteredItems.isEmpty()) {
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
        loadAllData();
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
                        loadAllData();
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

    private void showNewCanvasDialog() {
        EditText nameInput = new EditText(this);
        nameInput.setHint("название canvas");
        nameInput.setText("новый canvas");
        nameInput.setTextColor(android.graphics.Color.WHITE);
        
        new AlertDialog.Builder(this)
            .setTitle("создать canvas")
            .setView(nameInput)
            .setPositiveButton("создать", (dialog, which) -> {
                String name = nameInput.getText().toString().trim();
                if (name.isEmpty()) name = "новый canvas";
                createNewCanvas(name);
            })
            .setNegativeButton("отмена", null)
            .show();
    }

    private void showNewGraphDialog() {
        EditText nameInput = new EditText(this);
        nameInput.setHint("название графа");
        nameInput.setText("новый граф");
        nameInput.setTextColor(android.graphics.Color.WHITE);
        
        new AlertDialog.Builder(this)
            .setTitle("создать граф")
            .setView(nameInput)
            .setPositiveButton("создать", (dialog, which) -> {
                String name = nameInput.getText().toString().trim();
                if (name.isEmpty()) name = "новый граф";
                createNewGraph(name);
            })
            .setNegativeButton("отмена", null)
            .show();
    }

    private void createNewCanvas(String name) {
        apiService.createCanvas(name, 1920, 1080, new ApiService.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    JSONObject json = new JSONObject(result);
                    CanvasItem canvas = new CanvasItem();
                    canvas.setId(json.getInt("id"));
                    canvas.setName(json.getString("name"));
                    canvas.setCreatedAt(json.getString("created_at"));
                    canvas.setUpdatedAt(json.getString("updated_at"));
                    
                    allCanvases.add(canvas);
                    applyFilter(currentFilter);
                    
                    // открываем созданный canvas
                    Intent intent = new Intent(MainActivity.this, CanvasActivity.class);
                    intent.putExtra("canvas_id", canvas.getId());
                    intent.putExtra("canvas_name", canvas.getName());
                    startActivity(intent);
                } catch (JSONException e) {
                    Toast.makeText(MainActivity.this, "ошибка создания canvas", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this, "ошибка: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createNewGraph(String name) {
        apiService.saveGraphState(name, "{}", "force", 1.0, 0.0, 0.0, "", 
            new ApiService.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    JSONObject json = new JSONObject(result);
                    GraphItem graph = new GraphItem();
                    graph.setId(json.getInt("id"));
                    graph.setName(json.getString("name"));
                    graph.setLayout(json.optString("layout", "force"));
                    graph.setCreatedAt(json.getString("created_at"));
                    graph.setUpdatedAt(json.getString("updated_at"));
                    
                    allGraphs.add(graph);
                    applyFilter(currentFilter);
                    
                    // открываем созданный граф
                    Intent intent = new Intent(MainActivity.this, GraphActivity.class);
                    intent.putExtra("graph_id", graph.getId());
                    intent.putExtra("graph_name", graph.getName());
                    startActivity(intent);
                } catch (JSONException e) {
                    Toast.makeText(MainActivity.this, "ошибка создания графа", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this, "ошибка: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteCanvas(int canvasId) {
        apiService.deleteCanvas(canvasId, new ApiService.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                Toast.makeText(MainActivity.this, "Canvas удален", Toast.LENGTH_SHORT).show();
                loadAllData();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this, "Ошибка удаления: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteGraph(int graphId) {
        apiService.deleteGraphState(graphId, new ApiService.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                Toast.makeText(MainActivity.this, "Граф удален", Toast.LENGTH_SHORT).show();
                loadAllData();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(MainActivity.this, "Ошибка удаления: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
} 