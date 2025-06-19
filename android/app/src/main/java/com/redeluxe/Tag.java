package com.redeluxe;

public class Tag {
    private int id;
    private String name;
    private String color;
    private int user_id;
    private String created_at;

    public Tag() {}

    public Tag(String name, String color) {
        this.name = name;
        this.color = color;
    }

    // геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public int getUserId() { return user_id; }
    public void setUserId(int user_id) { this.user_id = user_id; }

    public String getCreatedAt() { return created_at; }
    public void setCreatedAt(String created_at) { this.created_at = created_at; }
} 