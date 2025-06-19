package com.redeluxe;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private List<ListItem> items;
    private OnNoteActionListener listener;

    public interface OnNoteActionListener {
        void onNoteClick(Note note);
        void onNoteArchive(Note note);
        void onNoteDelete(Note note);
        void onNotePin(Note note);
        void onNoteLongClick(Note note);
        void onCanvasClick(CanvasItem canvas);
        void onCanvasDelete(CanvasItem canvas);
        void onGraphClick(GraphItem graph);
        void onGraphDelete(GraphItem graph);
    }

    public NotesAdapter(List<ListItem> items, OnNoteActionListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        
        switch (viewType) {
            case ListItem.TYPE_CANVAS:
                View canvasView = inflater.inflate(R.layout.item_note, parent, false);
                return new CanvasViewHolder(canvasView);
            case ListItem.TYPE_GRAPH:
                View graphView = inflater.inflate(R.layout.item_note, parent, false);
                return new GraphViewHolder(graphView);
            default: // TYPE_NOTE
                View noteView = inflater.inflate(R.layout.item_note, parent, false);
                return new NoteViewHolder(noteView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ListItem item = items.get(position);
        
        switch (item.getType()) {
            case ListItem.TYPE_NOTE:
                ((NoteViewHolder) holder).bind(item.getNote());
                break;
            case ListItem.TYPE_CANVAS:
                ((CanvasViewHolder) holder).bind(item.getCanvas());
                break;
            case ListItem.TYPE_GRAPH:
                ((GraphViewHolder) holder).bind(item.getGraph());
                break;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // ViewHolder для заметок
    class NoteViewHolder extends RecyclerView.ViewHolder {
        private View colorIndicator;
        private TextView titleText, contentText, dateText, categoryText;
        private ImageView pinIcon;
        private ImageButton archiveButton, moreButton;
        private HorizontalScrollView tagsContainer;
        private LinearLayout tagsLayout;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            
            colorIndicator = itemView.findViewById(R.id.colorIndicator);
            titleText = itemView.findViewById(R.id.titleText);
            contentText = itemView.findViewById(R.id.contentText);
            dateText = itemView.findViewById(R.id.dateText);
            categoryText = itemView.findViewById(R.id.categoryText);
            pinIcon = itemView.findViewById(R.id.pinIcon);
            archiveButton = itemView.findViewById(R.id.archiveButton);
            moreButton = itemView.findViewById(R.id.moreButton);
            tagsContainer = itemView.findViewById(R.id.tagsContainer);
            tagsLayout = itemView.findViewById(R.id.tagsLayout);
        }

        public void bind(Note note) {
            // основные данные
            titleText.setText(note.getTitle().isEmpty() ? "без названия" : note.getTitle());
            contentText.setText(note.getContent().isEmpty() ? "пустая заметка" : note.getContent());
            
            // цвет
            try {
                colorIndicator.setBackgroundColor(Color.parseColor(note.getColor()));
            } catch (Exception e) {
                colorIndicator.setBackgroundColor(Color.parseColor("#00ffff"));
            }
            
            // дата
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM", Locale.getDefault());
                Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                        .parse(note.getCreatedAt());
                dateText.setText(date != null ? sdf.format(date) : "");
            } catch (Exception e) {
                dateText.setText("");
            }
            
            // закрепление
            pinIcon.setVisibility(note.isPinned() ? View.VISIBLE : View.GONE);
            
            // категория
            if (note.getCategory() != null) {
                categoryText.setText(note.getCategory().getIcon() + " " + note.getCategory().getName());
                categoryText.setVisibility(View.VISIBLE);
                try {
                    categoryText.setTextColor(Color.parseColor(note.getCategory().getColor()));
                } catch (Exception e) {
                    categoryText.setTextColor(Color.parseColor("#00ffff"));
                }
            } else {
                categoryText.setVisibility(View.GONE);
            }
            
            // теги скрыты пока
            tagsContainer.setVisibility(View.GONE);
            
            // обработчики
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNoteClick(note);
                }
            });
            
            archiveButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNoteArchive(note);
                }
            });
            
            moreButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNotePin(note);
                }
            });
            
            // долгий тап для контекстного меню
            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onNoteLongClick(note);
                }
                return true;
            });
        }
    }

    // ViewHolder для canvas
    class CanvasViewHolder extends RecyclerView.ViewHolder {
        private View colorIndicator;
        private TextView titleText, contentText, dateText, categoryText;
        private ImageView pinIcon;
        private ImageButton archiveButton, moreButton;

        public CanvasViewHolder(@NonNull View itemView) {
            super(itemView);
            
            colorIndicator = itemView.findViewById(R.id.colorIndicator);
            titleText = itemView.findViewById(R.id.titleText);
            contentText = itemView.findViewById(R.id.contentText);
            dateText = itemView.findViewById(R.id.dateText);
            categoryText = itemView.findViewById(R.id.categoryText);
            pinIcon = itemView.findViewById(R.id.pinIcon);
            archiveButton = itemView.findViewById(R.id.archiveButton);
            moreButton = itemView.findViewById(R.id.moreButton);
        }

        public void bind(CanvasItem canvas) {
            // основные данные
            titleText.setText("🎨 " + canvas.getName());
            contentText.setText("интерактивный canvas");
            
            // цвет для canvas
            colorIndicator.setBackgroundColor(Color.parseColor("#ff8000"));
            
            // дата
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM", Locale.getDefault());
                Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                        .parse(canvas.getUpdatedAt());
                dateText.setText(date != null ? sdf.format(date) : "");
            } catch (Exception e) {
                dateText.setText("");
            }
            
            // canvas не закрепляется
            pinIcon.setVisibility(View.GONE);
            
            // тип элемента
            categoryText.setText("🎨 canvas");
            categoryText.setTextColor(Color.parseColor("#ff8000"));
            categoryText.setVisibility(View.VISIBLE);
            
            // обработчики
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCanvasClick(canvas);
                }
            });
            
            // Скрываем ненужные кнопки
            archiveButton.setVisibility(View.GONE);
            moreButton.setVisibility(View.GONE);

            // Удаление по долгому тапу
            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onCanvasDelete(canvas);
                }
                return true;
            });
        }
    }

    // ViewHolder для графиков
    class GraphViewHolder extends RecyclerView.ViewHolder {
        private View colorIndicator;
        private TextView titleText, contentText, dateText, categoryText;
        private ImageView pinIcon;
        private ImageButton archiveButton, moreButton;

        public GraphViewHolder(@NonNull View itemView) {
            super(itemView);
            
            colorIndicator = itemView.findViewById(R.id.colorIndicator);
            titleText = itemView.findViewById(R.id.titleText);
            contentText = itemView.findViewById(R.id.contentText);
            dateText = itemView.findViewById(R.id.dateText);
            categoryText = itemView.findViewById(R.id.categoryText);
            pinIcon = itemView.findViewById(R.id.pinIcon);
            archiveButton = itemView.findViewById(R.id.archiveButton);
            moreButton = itemView.findViewById(R.id.moreButton);
        }

        public void bind(GraphItem graph) {
            // основные данные
            titleText.setText("📊 " + graph.getName());
            contentText.setText("граф связей (" + graph.getLayout() + ")");
            
            // цвет для графика
            colorIndicator.setBackgroundColor(Color.parseColor("#00ff41"));
            
            // дата
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("dd.MM", Locale.getDefault());
                Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                        .parse(graph.getUpdatedAt());
                dateText.setText(date != null ? sdf.format(date) : "");
            } catch (Exception e) {
                dateText.setText("");
            }
            
            // граф не закрепляется
            pinIcon.setVisibility(View.GONE);
            
            // тип элемента
            categoryText.setText("📊 граф");
            categoryText.setTextColor(Color.parseColor("#00ff41"));
            categoryText.setVisibility(View.VISIBLE);
            
            // обработчики
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onGraphClick(graph);
                }
            });
            
            // Скрываем ненужные кнопки
            archiveButton.setVisibility(View.GONE);
            moreButton.setVisibility(View.GONE);
            
            // Удаление по долгому тапу
            itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onGraphDelete(graph);
                }
                return true;
            });
        }
    }
} 