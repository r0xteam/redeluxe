package com.redeluxe;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import android.content.Intent;
import android.widget.TextView;

public class GraphActivity extends AppCompatActivity {
    private GraphView graphView;
    private ApiService apiService;
    private String currentLayout = "force";
    private String currentFilter = "";
    private int currentGraphId;
    private String currentGraphName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        apiService = new ApiService(this);
        graphView = findViewById(R.id.graphView);

        setupButtons();

        Intent intent = getIntent();
        currentGraphId = intent.getIntExtra("graph_id", 0);
        String graphName = intent.getStringExtra("graph_name");
        currentGraphName = graphName;
        
        TextView title = findViewById(R.id.graphTitle);
        if (graphName != null && !graphName.isEmpty()) {
            title.setText(graphName);
        }

        if (currentGraphId > 0) {
            loadGraphState(currentGraphId);
        } else {
            loadGraph(); // load default graph
        }
    }

    private void setupButtons() {
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.zoomInButton).setOnClickListener(v -> graphView.zoomIn());
        findViewById(R.id.zoomOutButton).setOnClickListener(v -> graphView.zoomOut());
        findViewById(R.id.centerButton).setOnClickListener(v -> graphView.centerGraph());
        findViewById(R.id.saveGraphButton).setOnClickListener(v -> saveGraphState());
        findViewById(R.id.menuButton).setOnClickListener(this::showGraphMenu);
    }

    private void showGraphMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Добавить узел").setOnMenuItemClickListener(item -> {
            showAddNodeDialog();
            return true;
        });
        popup.getMenu().add("Изменить лейаут").setOnMenuItemClickListener(item -> {
            showLayoutOptions();
            return true;
        });
        popup.getMenu().add("Фильтры").setOnMenuItemClickListener(item -> {
            showFilterOptions();
            return true;
        });
        popup.getMenu().add("Настройки графа").setOnMenuItemClickListener(item -> {
            showSettings();
            return true;
        });
        popup.show();
    }

    private void showLayoutOptions() {
        String[] layouts = {"force", "circular", "tree", "grid"};
        new AlertDialog.Builder(this)
                .setTitle("выбрать лейаут")
                .setItems(layouts, (dialog, which) -> {
                    currentLayout = layouts[which];
                    graphView.setLayout(currentLayout);
                })
                .show();
    }

    private void showFilterOptions() {
        String[] filters = {"", "type=note", "type=category", "type=tag"};
        String[] filterNames = {"все", "только заметки", "только категории", "только теги"};
        
        new AlertDialog.Builder(this)
            .setTitle("фильтры")
            .setItems(filterNames, (dialog, which) -> {
                currentFilter = filters[which];
                loadGraph();
                Toast.makeText(this, "фильтр применен", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("отмена", null)
            .show();
    }
    
    private void showAddNodeDialog() {
        EditText labelInput = new EditText(this);
        labelInput.setHint("название узла");
        labelInput.setTextColor(Color.WHITE);
        
        String[] types = {"note", "category", "tag", "person", "date"};
        String[] typeNames = {"заметка", "категория", "тег", "персона", "дата"};
        
        final int[] selectedType = {0};
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(labelInput);
        
        new AlertDialog.Builder(this)
            .setTitle("добавить узел")
            .setView(layout)
            .setSingleChoiceItems(typeNames, 0, (dialog, which) -> {
                selectedType[0] = which;
            })
            .setPositiveButton("создать", (dialog, which) -> {
                String label = labelInput.getText().toString().trim();
                if (label.isEmpty()) label = "новый узел";
                graphView.addNewNode(label, types[selectedType[0]]);
            })
            .setNegativeButton("отмена", null)
            .show();
    }

    private void showSettings() {
        Toast.makeText(this, "настройки графа", Toast.LENGTH_SHORT).show();
    }

    private void loadGraph() {
        apiService.getGraph(currentFilter, new ApiService.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    graphView.loadGraphFromJson(result);
                    Toast.makeText(GraphActivity.this, "граф загружен", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    // если ошибка загрузки, используем демо граф
                    graphView.createDemoGraph();
                    Toast.makeText(GraphActivity.this, "демо граф загружен", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                graphView.createDemoGraph();
                Toast.makeText(GraphActivity.this, "демо граф загружен", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveGraphState() {
        if (graphView == null) return;

        if (currentGraphId > 0 && currentGraphName != null && !currentGraphName.isEmpty()) {
            // если граф был загружен, обновляем его состояние без диалога
            GraphView.ViewportState state = graphView.getViewportState();
            String graphData = graphView.getGraphDataAsJson();
            
            apiService.saveGraphState(currentGraphName, graphData, currentLayout, 
                state.zoom, state.panX, state.panY, currentFilter, 
                new ApiService.ApiCallback<String>() {
                @Override
                public void onSuccess(String result) {
                    Toast.makeText(GraphActivity.this, "состояние '" + currentGraphName + "' обновлено", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(GraphActivity.this, "ошибка обновления: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // если это новый граф, запрашиваем имя
            EditText nameInput = new EditText(this);
            nameInput.setHint("название состояния графа");
            nameInput.setTextColor(Color.WHITE);
            
            new AlertDialog.Builder(this)
                .setTitle("сохранить новое состояние графа")
                .setView(nameInput)
                .setPositiveButton("сохранить", (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    if (name.isEmpty()) name = "граф_" + System.currentTimeMillis();
                    
                    GraphView.ViewportState state = graphView.getViewportState();
                    String graphData = graphView.getGraphDataAsJson();
                    
                    final String finalName = name;
                    apiService.saveGraphState(finalName, graphData, currentLayout, 
                        state.zoom, state.panX, state.panY, currentFilter, 
                        new ApiService.ApiCallback<String>() {
                        @Override
                        public void onSuccess(String result) {
                            Toast.makeText(GraphActivity.this, "состояние '" + finalName + "' сохранено", Toast.LENGTH_SHORT).show();
                            try {
                                JSONObject json = new JSONObject(result);
                                currentGraphId = json.getInt("id");
                                currentGraphName = json.getString("name");
                                ((TextView) findViewById(R.id.graphTitle)).setText(currentGraphName);
                            } catch (JSONException e) {
                                //
                            }
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(GraphActivity.this, "ошибка сохранения: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("отмена", null)
                .show();
        }
    }

    private void loadGraphState(int stateId) {
        apiService.getGraphState(stateId, new ApiService.ApiCallback<String>() {
            @Override
            public void onSuccess(String result) {
                try {
                    graphView.loadGraphStateFromJson(result);
                    Toast.makeText(GraphActivity.this, "состояние загружено", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(GraphActivity.this, "ошибка загрузки состояния", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                Toast.makeText(GraphActivity.this, "ошибка: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static class GraphView extends View {
        private List<GraphNode> nodes = new ArrayList<>();
        private List<GraphEdge> edges = new ArrayList<>();
        private Paint nodePaint, edgePaint, textPaint, backgroundPaint;
        private GraphNode draggedNode = null;
        private GraphNode selectedNode = null;
        
        // long press detection
        private Handler longPressHandler = new Handler();
        private Runnable longPressRunnable;
        private boolean isLongPress = false;
        private static final int LONG_PRESS_DELAY = 500;
        
        // physics simulation
        private ValueAnimator physicsAnimator;
        private boolean physicsEnabled = true;
        private static final float REPULSION_FORCE = 500f;
        private static final float SPRING_FORCE = 0.01f;
        private static final float DAMPING = 0.9f;
        
        // zoom and pan
        private Matrix matrix = new Matrix();
        private Matrix savedMatrix = new Matrix();
        private ScaleGestureDetector scaleDetector;
        private float scaleFactor = 1.0f;
        private PointF start = new PointF();
        private PointF mid = new PointF();
        private int mode = NONE;
        
        private static final int NONE = 0;
        private static final int DRAG = 1;
        private static final int ZOOM = 2;
        
        private float lastTouchX, lastTouchY;

        public GraphView(Context context, AttributeSet attrs) {
            super(context, attrs);
            init();
        }

        private void init() {
            initPaints();
            scaleDetector = new ScaleGestureDetector(getContext(), new ScaleListener());
            startPhysicsSimulation();
        }

        private void initPaints() {
            nodePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            nodePaint.setStyle(Paint.Style.FILL);

            edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            edgePaint.setStyle(Paint.Style.STROKE);
            edgePaint.setStrokeWidth(3);

            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(24);
            textPaint.setTextAlign(Paint.Align.CENTER);
            
            backgroundPaint = new Paint();
            backgroundPaint.setColor(0xFF0a0a0a);
        }

        public void createDemoGraph() {
            nodes.clear();
            edges.clear();
            
            // центральные узлы
            GraphNode main = createNode("main", "главная", "note", "#00ffff", 50, 400, 300);
            GraphNode idea1 = createNode("idea1", "идея 1", "note", "#ff0080", 40, 200, 150);
            GraphNode idea2 = createNode("idea2", "идея 2", "note", "#00ff41", 40, 600, 150);
            GraphNode detail1 = createNode("detail1", "детали", "note", "#ff8000", 35, 150, 450);
            GraphNode detail2 = createNode("detail2", "примеры", "note", "#8000ff", 35, 650, 450);
            
            // категории
            GraphNode cat1 = createNode("cat1", "работа", "category", "#ff0080", 25, 100, 100);
            GraphNode cat2 = createNode("cat2", "учеба", "category", "#00ff41", 25, 700, 100);
            
            // теги
            GraphNode tag1 = createNode("tag1", "важное", "tag", "#00ffff", 20, 350, 100);
            GraphNode tag2 = createNode("tag2", "todo", "tag", "#ff8000", 20, 450, 100);
            
            nodes.add(main);
            nodes.add(idea1);
            nodes.add(idea2);
            nodes.add(detail1);
            nodes.add(detail2);
            nodes.add(cat1);
            nodes.add(cat2);
            nodes.add(tag1);
            nodes.add(tag2);

            // связи
            edges.add(new GraphEdge(main, idea1, "связь", 2));
            edges.add(new GraphEdge(main, idea2, "связь", 2));
            edges.add(new GraphEdge(idea1, detail1, "детализация", 1));
            edges.add(new GraphEdge(idea2, detail2, "пример", 1));
            edges.add(new GraphEdge(idea1, cat1, "категория", 1));
            edges.add(new GraphEdge(idea2, cat2, "категория", 1));
            edges.add(new GraphEdge(main, tag1, "тег", 1));
            edges.add(new GraphEdge(main, tag2, "тег", 1));
            
            invalidate();
        }

        private GraphNode createNode(String id, String label, String type, String color, int size, float x, float y) {
            GraphNode node = new GraphNode();
            node.id = id;
            node.label = label;
            node.type = type;
            node.color = color;
            node.size = size;
            node.x = x;
            node.y = y;
            return node;
        }

        private void startPhysicsSimulation() {
            physicsAnimator = ValueAnimator.ofFloat(0f, 1f);
            physicsAnimator.setDuration(Long.MAX_VALUE);
            physicsAnimator.setRepeatCount(ValueAnimator.INFINITE);
            physicsAnimator.addUpdateListener(animation -> {
                if (physicsEnabled && draggedNode == null) {
                    updatePhysics();
                    invalidate();
                }
            });
            physicsAnimator.start();
        }

        private void updatePhysics() {
            // repulsion between nodes
            for (int i = 0; i < nodes.size(); i++) {
                GraphNode node1 = nodes.get(i);
                node1.fx = 0;
                node1.fy = 0;
                
                for (int j = 0; j < nodes.size(); j++) {
                    if (i == j) continue;
                    GraphNode node2 = nodes.get(j);
                    
                    float dx = node1.x - node2.x;
                    float dy = node1.y - node2.y;
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    
                    if (distance > 0) {
                        float force = REPULSION_FORCE / (distance * distance);
                        node1.fx += force * dx / distance;
                        node1.fy += force * dy / distance;
                    }
                }
            }
            
            // spring forces for connected nodes
            for (GraphEdge edge : edges) {
                GraphNode source = edge.source;
                GraphNode target = edge.target;
                
                float dx = target.x - source.x;
                float dy = target.y - source.y;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float idealDistance = 150f * edge.weight;
                
                float force = SPRING_FORCE * (distance - idealDistance);
                
                source.fx += force * dx / distance;
                source.fy += force * dy / distance;
                target.fx -= force * dx / distance;
                target.fy -= force * dy / distance;
            }
            
            // apply forces and damping
            for (GraphNode node : nodes) {
                node.vx = (node.vx + node.fx) * DAMPING;
                node.vy = (node.vy + node.fy) * DAMPING;
                
                node.x += node.vx;
                node.y += node.vy;
                
                // boundaries
                node.x = Math.max(node.size, Math.min(getWidth() - node.size, node.x));
                node.y = Math.max(node.size, Math.min(getHeight() - node.size, node.y));
            }
        }

        public void zoomIn() {
            scaleFactor *= 1.2f;
            matrix.setScale(scaleFactor, scaleFactor);
            invalidate();
        }

        public void zoomOut() {
            scaleFactor /= 1.2f;
            matrix.setScale(scaleFactor, scaleFactor);
            invalidate();
        }

        public void centerGraph() {
            scaleFactor = 1.0f;
            matrix.reset();
            invalidate();
        }
        
        public void addNewNode(String label, String type) {
            GraphNode node = new GraphNode();
            node.id = "node_" + System.currentTimeMillis();
            node.label = label;
            node.type = type;
            node.color = getRandomColor();
            node.size = getDefaultSizeForType(type);
            node.x = 200 + (float)(Math.random() * 400);
            node.y = 200 + (float)(Math.random() * 300);
            nodes.add(node);
            invalidate();
            Toast.makeText(getContext(), "узел \"" + label + "\" создан", Toast.LENGTH_SHORT).show();
        }
        
        private String getRandomColor() {
            String[] colors = {"#00ffff", "#ff0080", "#00ff41", "#ff8000", "#8000ff"};
            return colors[(int)(Math.random() * colors.length)];
        }
        
        private int getDefaultSizeForType(String type) {
            switch (type) {
                case "note": return 45;
                case "category": return 35;
                case "tag": return 25;
                case "person": return 40;
                case "date": return 30;
                default: return 35;
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            
            // черный фон
            canvas.drawPaint(backgroundPaint);
            
            canvas.save();
            canvas.concat(matrix);

            // рисуем рёбра
            for (GraphEdge edge : edges) {
                drawEdge(canvas, edge);
            }

            // рисуем узлы
            for (GraphNode node : nodes) {
                drawNode(canvas, node);
            }

            canvas.restore();
        }

        private void drawEdge(Canvas canvas, GraphEdge edge) {
            GraphNode source = edge.source;
            GraphNode target = edge.target;
            
            // выбираем цвет по типу связи
            switch (edge.type) {
                case "связь":
                    edgePaint.setColor(Color.parseColor("#00ffff"));
                    break;
                case "категория":
                    edgePaint.setColor(Color.parseColor("#ff0080"));
                    break;
                case "тег":
                    edgePaint.setColor(Color.parseColor("#00ff41"));
                    break;
                default:
                    edgePaint.setColor(Color.parseColor("#666666"));
                    break;
            }
            
            edgePaint.setAlpha(150);
            edgePaint.setStrokeWidth(edge.weight * 2);
            
            // curved line
            Path path = new Path();
            path.moveTo(source.x, source.y);
            
            float midX = (source.x + target.x) / 2;
            float midY = (source.y + target.y) / 2;
            float ctrlX = midX + (float)(Math.random() - 0.5) * 50;
            float ctrlY = midY - 30;
            
            path.quadTo(ctrlX, ctrlY, target.x, target.y);
            canvas.drawPath(path, edgePaint);
        }

        private void drawNode(Canvas canvas, GraphNode node) {
            try {
                nodePaint.setColor(Color.parseColor(node.color));
            } catch (Exception e) {
                nodePaint.setColor(Color.parseColor("#00ffff"));
            }

            // основной круг
            nodePaint.setAlpha(200);
            canvas.drawCircle(node.x, node.y, node.size, nodePaint);
            
            // граница
            Paint borderPaint = new Paint(nodePaint);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(node == selectedNode ? 5 : 3);
            borderPaint.setColor(node == selectedNode ? Color.WHITE : Color.parseColor(node.color));
            borderPaint.setAlpha(255);
            canvas.drawCircle(node.x, node.y, node.size, borderPaint);
            
            // внутренний круг для категорий и тегов
            if (!node.type.equals("note")) {
                nodePaint.setAlpha(100);
                canvas.drawCircle(node.x, node.y, node.size * 0.7f, nodePaint);
            }
            
            // текст
            String label = node.label.length() > 8 ? 
                node.label.substring(0, 8) + "..." : node.label;
            
            textPaint.setTextSize(node.size / 2.5f);
            canvas.drawText(label, node.x, node.y + node.size + 25, textPaint);
            
            // иконка типа
            textPaint.setTextSize(node.size / 2);
            String icon = getNodeIcon(node.type);
            canvas.drawText(icon, node.x, node.y + 8, textPaint);
        }

        private String getNodeIcon(String type) {
            switch (type) {
                case "note": return "📝";
                case "category": return "📁";
                case "tag": return "🏷️";
                case "person": return "👤";
                case "date": return "📅";
                default: return "◆";
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            scaleDetector.onTouchEvent(event);
            
            float x = event.getX();
            float y = event.getY();
            
            // transform coordinates
            float[] coords = {x, y};
            Matrix inverse = new Matrix();
            matrix.invert(inverse);
            inverse.mapPoints(coords);
            x = coords[0];
            y = coords[1];

            switch (event.getAction() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_DOWN:
                    savedMatrix.set(matrix);
                    start.set(event.getX(), event.getY());
                    mode = DRAG;
                    isLongPress = false;
                    
                    draggedNode = findNodeAtPosition(x, y);
                    if (draggedNode != null) {
                        selectedNode = draggedNode;
                        lastTouchX = x;
                        lastTouchY = y;
                        draggedNode.vx = 0;
                        draggedNode.vy = 0;
                        
                        // setup long press detection
                        final GraphNode nodeForLongPress = draggedNode;
                        longPressRunnable = () -> {
                            isLongPress = true;
                            showNodeEditDialog(nodeForLongPress);
                        };
                        longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_DELAY);
                        
                        invalidate();
                        return true;
                    }
                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    float dx = event.getX(0) - event.getX(1);
                    float dy = event.getY(0) - event.getY(1);
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d > 10f) {
                        savedMatrix.set(matrix);
                        midPoint(mid, event);
                        mode = ZOOM;
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    // cancel long press on move
                    if (longPressRunnable != null) {
                        longPressHandler.removeCallbacks(longPressRunnable);
                    }
                    
                    if (mode == DRAG && !isLongPress) {
                        if (draggedNode != null) {
                            float deltaX = x - lastTouchX;
                            float deltaY = y - lastTouchY;
                            
                            draggedNode.x += deltaX;
                            draggedNode.y += deltaY;
                            
                            lastTouchX = x;
                            lastTouchY = y;
                            
                            invalidate();
                            return true;
                        } else {
                            matrix.set(savedMatrix);
                            matrix.postTranslate(event.getX() - start.x, event.getY() - start.y);
                            invalidate();
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    // cancel long press
                    if (longPressRunnable != null) {
                        longPressHandler.removeCallbacks(longPressRunnable);
                    }
                    
                    mode = NONE;
                    draggedNode = null;
                    isLongPress = false;
                    break;
            }

            return true;
        }

        private void midPoint(PointF point, MotionEvent event) {
            float x = event.getX(0) + event.getX(1);
            float y = event.getY(0) + event.getY(1);
            point.set(x / 2, y / 2);
        }

        private GraphNode findNodeAtPosition(float x, float y) {
            for (GraphNode node : nodes) {
                float distance = (float) Math.sqrt(
                    Math.pow(x - node.x, 2) + Math.pow(y - node.y, 2)
                );
                if (distance <= node.size) {
                    return node;
                }
            }
            return null;
        }

        public void showNodeEditDialog(GraphNode node) {
            String[] options = {
                "📝 редактировать",
                "🎨 изменить цвет",
                "📏 размер",
                "📋 дублировать", 
                "🔗 связать с...",
                "🗑️ удалить"
            };
            
            new AlertDialog.Builder(getContext())
                .setTitle(node.label + " " + getNodeIcon(node.type))
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: editNodeLabel(node); break;
                        case 1: changeNodeColor(node); break;
                        case 2: changeNodeSize(node); break;
                        case 3: duplicateNode(node); break;
                        case 4: connectToNode(node); break;
                        case 5: deleteNode(node); break;
                    }
                })
                .setNegativeButton("отмена", null)
                .show();
        }
        
        private void editNodeLabel(GraphNode node) {
            EditText labelInput = new EditText(getContext());
            labelInput.setHint("название узла");
            labelInput.setText(node.label);
            labelInput.setTextColor(Color.WHITE);
            
            String[] types = {"note", "category", "tag", "person", "date"};
            String[] typeNames = {"заметка", "категория", "тег", "персона", "дата"};
            int currentType = 0;
            for (int i = 0; i < types.length; i++) {
                if (types[i].equals(node.type)) {
                    currentType = i;
                    break;
                }
            }
            
            LinearLayout layout = new LinearLayout(getContext());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(labelInput);
            
            new AlertDialog.Builder(getContext())
                .setTitle("редактировать узел")
                .setView(layout)
                .setSingleChoiceItems(typeNames, currentType, (dialog, which) -> {
                    node.type = types[which];
                })
                .setPositiveButton("сохранить", (dialog, which) -> {
                    node.label = labelInput.getText().toString().trim();
                    if (node.label.isEmpty()) node.label = "узел";
                    invalidate();
                })
                .setNegativeButton("отмена", null)
                .show();
        }
        
        private void changeNodeColor(GraphNode node) {
            String[] colors = {"#00ffff", "#ff0080", "#00ff41", "#ff8000", "#8000ff", "#ffff00", "#ff4444"};
            String[] colorNames = {"cyan", "pink", "green", "orange", "purple", "yellow", "red"};
            
            new AlertDialog.Builder(getContext())
                .setTitle("выбрать цвет")
                .setItems(colorNames, (dialog, which) -> {
                    node.color = colors[which];
                    invalidate();
                })
                .setNegativeButton("отмена", null)
                .show();
        }
        
        private void changeNodeSize(GraphNode node) {
            String[] sizes = {"маленький", "средний", "большой", "огромный"};
            int[] sizeValues = {20, 35, 50, 70};
            
            int currentSize = 1; // default medium
            for (int i = 0; i < sizeValues.length; i++) {
                if (Math.abs(sizeValues[i] - node.size) < 5) {
                    currentSize = i;
                    break;
                }
            }
            
            new AlertDialog.Builder(getContext())
                .setTitle("размер узла")
                .setSingleChoiceItems(sizes, currentSize, (dialog, which) -> {
                    node.size = sizeValues[which];
                    invalidate();
                    dialog.dismiss();
                })
                .setNegativeButton("отмена", null)
                .show();
        }
        
        private void duplicateNode(GraphNode original) {
            GraphNode copy = new GraphNode();
            copy.id = original.id + "_copy";
            copy.label = original.label + " (копия)";
            copy.type = original.type;
            copy.color = original.color;
            copy.size = original.size;
            copy.x = original.x + 100;
            copy.y = original.y + 100;
            nodes.add(copy);
            invalidate();
            Toast.makeText(getContext(), "узел дублирован", Toast.LENGTH_SHORT).show();
        }
        
        private void connectToNode(GraphNode sourceNode) {
            if (nodes.size() < 2) {
                Toast.makeText(getContext(), "нужно минимум 2 узла", Toast.LENGTH_SHORT).show();
                return;
            }
            
            List<String> nodeLabels = new ArrayList<>();
            List<GraphNode> availableNodes = new ArrayList<>();
            
            for (GraphNode node : nodes) {
                if (node != sourceNode) {
                    nodeLabels.add(node.label + " (" + node.type + ")");
                    availableNodes.add(node);
                }
            }
            
            new AlertDialog.Builder(getContext())
                .setTitle("связать с узлом")
                .setItems(nodeLabels.toArray(new String[0]), (dialog, which) -> {
                    GraphNode targetNode = availableNodes.get(which);
                    
                    // check if connection already exists
                    boolean exists = false;
                    for (GraphEdge edge : edges) {
                        if ((edge.source == sourceNode && edge.target == targetNode) ||
                            (edge.source == targetNode && edge.target == sourceNode)) {
                            exists = true;
                            break;
                        }
                    }
                    
                    if (!exists) {
                        edges.add(new GraphEdge(sourceNode, targetNode, "связь", 1));
                        Toast.makeText(getContext(), "связь создана", Toast.LENGTH_SHORT).show();
                        invalidate();
                    } else {
                        Toast.makeText(getContext(), "связь уже существует", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("отмена", null)
                .show();
        }
        
        private void deleteNode(GraphNode node) {
            new AlertDialog.Builder(getContext())
                .setTitle("удалить узел?")
                .setMessage("\"" + node.label + "\" и все связи будут удалены")
                .setPositiveButton("удалить", (dialog, which) -> {
                    nodes.remove(node);
                    // remove all edges connected to this node
                    edges.removeIf(edge -> edge.source == node || edge.target == node);
                    if (selectedNode == node) selectedNode = null;
                    invalidate();
                    Toast.makeText(getContext(), "узел удален", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("отмена", null)
                .show();
        }

        public ViewportState getViewportState() {
            ViewportState state = new ViewportState();
            state.zoom = scaleFactor;
            float[] values = new float[9];
            matrix.getValues(values);
            state.panX = values[Matrix.MTRANS_X];
            state.panY = values[Matrix.MTRANS_Y];
            return state;
        }

        public String getGraphDataAsJson() {
            try {
                JSONObject data = new JSONObject();
                
                // nodes
                JSONArray nodesArray = new JSONArray();
                for (GraphNode node : nodes) {
                    JSONObject nodeJson = new JSONObject();
                    nodeJson.put("id", node.id);
                    nodeJson.put("label", node.label);
                    nodeJson.put("type", node.type);
                    nodeJson.put("color", node.color);
                    nodeJson.put("size", node.size);
                    nodeJson.put("x", node.x);
                    nodeJson.put("y", node.y);
                    nodesArray.put(nodeJson);
                }
                data.put("nodes", nodesArray);
                
                // edges
                JSONArray edgesArray = new JSONArray();
                for (GraphEdge edge : edges) {
                    JSONObject edgeJson = new JSONObject();
                    edgeJson.put("source", edge.source.id);
                    edgeJson.put("target", edge.target.id);
                    edgeJson.put("type", edge.type);
                    edgeJson.put("weight", edge.weight);
                    edgesArray.put(edgeJson);
                }
                data.put("edges", edgesArray);
                
                return data.toString();
            } catch (JSONException e) {
                return "{}";
            }
        }

        public void loadGraphFromJson(String jsonString) {
            try {
                JSONObject response = new JSONObject(jsonString);
                
                nodes.clear();
                edges.clear();
                
                // parse graph data from server response
                if (response.has("nodes")) {
                    JSONArray nodesArray = response.getJSONArray("nodes");
                    for (int i = 0; i < nodesArray.length(); i++) {
                        JSONObject nodeJson = nodesArray.getJSONObject(i);
                        GraphNode node = new GraphNode();
                        node.id = nodeJson.getString("id");
                        node.label = nodeJson.getString("label");
                        node.type = nodeJson.getString("type");
                        node.color = nodeJson.getString("color");
                        node.size = nodeJson.getInt("size");
                        node.x = (float) nodeJson.optDouble("x", Math.random() * 600 + 100);
                        node.y = (float) nodeJson.optDouble("y", Math.random() * 400 + 100);
                        nodes.add(node);
                    }
                }
                
                if (response.has("edges")) {
                    JSONArray edgesArray = response.getJSONArray("edges");
                    for (int i = 0; i < edgesArray.length(); i++) {
                        JSONObject edgeJson = edgesArray.getJSONObject(i);
                        String sourceId = edgeJson.getString("source");
                        String targetId = edgeJson.getString("target");
                        
                        GraphNode source = findNodeById(sourceId);
                        GraphNode target = findNodeById(targetId);
                        
                        if (source != null && target != null) {
                            edges.add(new GraphEdge(source, target, 
                                edgeJson.getString("type"), edgeJson.getInt("weight")));
                        }
                    }
                }
                
                invalidate();
            } catch (JSONException e) {
                createDemoGraph();
            }
        }

        public void loadGraphStateFromJson(String jsonString) {
            try {
                JSONObject state = new JSONObject(jsonString);
                
                // load viewport state
                if (state.has("zoom")) {
                    scaleFactor = (float) state.getDouble("zoom");
                }
                if (state.has("pan_x") && state.has("pan_y")) {
                    float panX = (float) state.getDouble("pan_x");
                    float panY = (float) state.getDouble("pan_y");
                    matrix.setTranslate(panX, panY);
                    matrix.postScale(scaleFactor, scaleFactor);
                }
                
                // load graph data
                if (state.has("data")) {
                    String graphData = state.getString("data");
                    loadGraphDataFromJson(graphData);
                }
                
                invalidate();
            } catch (JSONException e) {
                // ошибка загрузки состояния
            }
        }

        private void loadGraphDataFromJson(String jsonString) {
            try {
                JSONObject data = new JSONObject(jsonString);
                
                nodes.clear();
                edges.clear();
                
                if (data.has("nodes")) {
                    JSONArray nodesArray = data.getJSONArray("nodes");
                    for (int i = 0; i < nodesArray.length(); i++) {
                        JSONObject nodeJson = nodesArray.getJSONObject(i);
                        GraphNode node = new GraphNode();
                        node.id = nodeJson.getString("id");
                        node.label = nodeJson.getString("label");
                        node.type = nodeJson.getString("type");
                        node.color = nodeJson.getString("color");
                        node.size = nodeJson.getInt("size");
                        node.x = (float) nodeJson.getDouble("x");
                        node.y = (float) nodeJson.getDouble("y");
                        nodes.add(node);
                    }
                }
                
                if (data.has("edges")) {
                    JSONArray edgesArray = data.getJSONArray("edges");
                    for (int i = 0; i < edgesArray.length(); i++) {
                        JSONObject edgeJson = edgesArray.getJSONObject(i);
                        String sourceId = edgeJson.getString("source");
                        String targetId = edgeJson.getString("target");
                        
                        GraphNode source = findNodeById(sourceId);
                        GraphNode target = findNodeById(targetId);
                        
                        if (source != null && target != null) {
                            edges.add(new GraphEdge(source, target, 
                                edgeJson.getString("type"), edgeJson.getInt("weight")));
                        }
                    }
                }
            } catch (JSONException e) {
                // ошибка парсинга
            }
        }

        private GraphNode findNodeById(String id) {
            for (GraphNode node : nodes) {
                if (node.id.equals(id)) {
                    return node;
                }
            }
            return null;
        }

        public void setLayout(String layout) {
            // todo: implement different layout algorithms
            switch (layout) {
                case "circular":
                    arrangeCircular();
                    break;
                case "tree":
                    arrangeTree();
                    break;
                case "grid":
                    arrangeGrid();
                    break;
                default:
                    // force layout - уже работает через физику
                    break;
            }
            invalidate();
        }

        private void arrangeCircular() {
            float centerX = getWidth() / 2f;
            float centerY = getHeight() / 2f;
            float radius = Math.min(centerX, centerY) * 0.8f;
            
            for (int i = 0; i < nodes.size(); i++) {
                float angle = (float) (2 * Math.PI * i / nodes.size());
                GraphNode node = nodes.get(i);
                node.x = centerX + radius * (float) Math.cos(angle);
                node.y = centerY + radius * (float) Math.sin(angle);
                node.vx = 0;
                node.vy = 0;
            }
        }

        private void arrangeTree() {
            // простое древовидное расположение
            float startY = 100;
            float levelHeight = 150;
            int currentLevel = 0;
            int nodesPerLevel = (int) Math.ceil(Math.sqrt(nodes.size()));
            
            for (int i = 0; i < nodes.size(); i++) {
                GraphNode node = nodes.get(i);
                int level = i / nodesPerLevel;
                int position = i % nodesPerLevel;
                
                node.x = 100 + position * (getWidth() - 200) / Math.max(1, nodesPerLevel - 1);
                node.y = startY + level * levelHeight;
                node.vx = 0;
                node.vy = 0;
            }
        }

        private void arrangeGrid() {
            int cols = (int) Math.ceil(Math.sqrt(nodes.size()));
            int rows = (int) Math.ceil((double) nodes.size() / cols);
            
            float cellWidth = (getWidth() - 200) / Math.max(1, cols - 1);
            float cellHeight = (getHeight() - 200) / Math.max(1, rows - 1);
            
            for (int i = 0; i < nodes.size(); i++) {
                GraphNode node = nodes.get(i);
                int row = i / cols;
                int col = i % cols;
                
                node.x = 100 + col * cellWidth;
                node.y = 100 + row * cellHeight;
                node.vx = 0;
                node.vy = 0;
            }
        }

        private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(0.1f, Math.min(scaleFactor, 5.0f));
                matrix.setScale(scaleFactor, scaleFactor);
                invalidate();
                return true;
            }
        }

        public static class ViewportState {
            public double zoom = 1.0;
            public double panX = 0.0;
            public double panY = 0.0;
        }
    }

    public static class GraphNode {
        public String id;
        public String label;
        public String type;
        public String color;
        public int size;
        public float x, y;
        public float vx = 0, vy = 0; // velocity
        public float fx = 0, fy = 0; // forces
        public boolean isDragging = false;
    }

    public static class GraphEdge {
        public GraphNode source;
        public GraphNode target;
        public String type;
        public int weight;

        public GraphEdge(GraphNode source, GraphNode target, String type, int weight) {
            this.source = source;
            this.target = target;
            this.type = type;
            this.weight = weight;
        }
    }
} 