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

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {
    private List<Note> notes;
    private OnNoteActionListener listener;

    public interface OnNoteActionListener {
        void onNoteClick(Note note);
        void onNoteArchive(Note note);
        void onNoteDelete(Note note);
        void onNotePin(Note note);
        void onNoteLongClick(Note note);
    }

    public NotesAdapter(List<Note> notes, OnNoteActionListener listener) {
        this.notes = notes;
        this.listener = listener;
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = notes.get(position);
        holder.bind(note);
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

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
            
            // TODO: теги (когда добавим их в Note модель)
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
} 