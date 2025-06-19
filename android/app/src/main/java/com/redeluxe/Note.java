package com.redeluxe;

import java.util.List;

public class Note {
    private int id;
    private String title;
    private String content;
    private String color;
    private boolean is_encrypted;
    private boolean is_pinned;
    private boolean is_archived;
    private Integer category_id;
    private Category category;
    private List<Tag> tags;
    private String created_at;
    private String updated_at;

    public Note() {}

    public Note(String title, String content, String color) {
        this.title = title;
        this.content = content;
        this.color = color;
        this.is_encrypted = false;
        this.is_pinned = false;
        this.is_archived = false;
    }

    // геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public boolean isEncrypted() { return is_encrypted; }
    public void setEncrypted(boolean is_encrypted) { this.is_encrypted = is_encrypted; }

    public boolean isPinned() { return is_pinned; }
    public void setPinned(boolean is_pinned) { this.is_pinned = is_pinned; }

    public boolean isArchived() { return is_archived; }
    public void setArchived(boolean is_archived) { this.is_archived = is_archived; }

    public Integer getCategoryId() { return category_id; }
    public void setCategoryId(Integer category_id) { this.category_id = category_id; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public List<Tag> getTags() { return tags; }
    public void setTags(List<Tag> tags) { this.tags = tags; }

    public String getCreatedAt() { return created_at; }
    public void setCreatedAt(String created_at) { this.created_at = created_at; }

    public String getUpdatedAt() { return updated_at; }
    public void setUpdatedAt(String updated_at) { this.updated_at = updated_at; }
} 