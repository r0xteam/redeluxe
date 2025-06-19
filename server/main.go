package main

import (
	"crypto/rand"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/crypto/bcrypt"
	"gorm.io/datatypes"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
)

var db *gorm.DB
var jwtSecret = []byte("redeluxe_secret_2025")

type User struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	Email     string    `json:"email" gorm:"unique"`
	Password  string    `json:"-"`
	Username  string    `json:"username"`
	Avatar    string    `json:"avatar"`
	Theme     string    `json:"theme" gorm:"default:'dark'"`
	Settings  string    `json:"settings" gorm:"type:text"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

type Category struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	Name      string    `json:"name"`
	Color     string    `json:"color" gorm:"default:'#00ffff'"`
	Icon      string    `json:"icon" gorm:"default:'📁'"`
	UserID    uint      `json:"user_id"`
	User      User      `json:"user" gorm:"foreignKey:UserID"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

type Tag struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	Name      string    `json:"name"`
	Color     string    `json:"color" gorm:"default:'#ff0080'"`
	UserID    uint      `json:"user_id"`
	User      User      `json:"user" gorm:"foreignKey:UserID"`
	CreatedAt time.Time `json:"created_at"`
}

type Template struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	Name      string    `json:"name"`
	Content   string    `json:"content" gorm:"type:text"`
	Category  string    `json:"category" gorm:"default:'general'"`
	IsPublic  bool      `json:"is_public" gorm:"default:false"`
	UserID    uint      `json:"user_id"`
	User      User      `json:"user" gorm:"foreignKey:UserID"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

type Note struct {
	ID          uint          `json:"id" gorm:"primaryKey"`
	Title       string        `json:"title"`
	Content     string        `json:"content" gorm:"type:text"`
	Color       string        `json:"color" gorm:"default:'#00ffff'"`
	IsEncrypted bool          `json:"is_encrypted" gorm:"default:false"`
	IsPinned    bool          `json:"is_pinned" gorm:"default:false"`
	IsArchived  bool          `json:"is_archived" gorm:"default:false"`
	IsFavorite  bool          `json:"is_favorite" gorm:"default:false"`
	IsMarkdown  bool          `json:"is_markdown" gorm:"default:false"`
	Position    int           `json:"position" gorm:"default:0"`
	CategoryID  *uint         `json:"category_id"`
	Category    *Category     `json:"category,omitempty" gorm:"foreignKey:CategoryID"`
	UserID      uint          `json:"user_id"`
	User        User          `json:"user" gorm:"foreignKey:UserID"`
	Tags        []Tag         `json:"tags" gorm:"many2many:note_tags;"`
	Reminders   []Reminder    `json:"reminders,omitempty" gorm:"foreignKey:NoteID"`
	Files       []File        `json:"files,omitempty" gorm:"foreignKey:NoteID"`
	History     []NoteHistory `json:"history,omitempty" gorm:"foreignKey:NoteID"`
	CreatedAt   time.Time     `json:"created_at"`
	UpdatedAt   time.Time     `json:"updated_at"`
}

type Reminder struct {
	ID         uint      `json:"id" gorm:"primaryKey"`
	Title      string    `json:"title"`
	DateTime   time.Time `json:"date_time"`
	IsActive   bool      `json:"is_active" gorm:"default:true"`
	IsRepeat   bool      `json:"is_repeat" gorm:"default:false"`
	RepeatType string    `json:"repeat_type"` // daily, weekly, monthly
	NoteID     *uint     `json:"note_id"`
	Note       *Note     `json:"note,omitempty" gorm:"foreignKey:NoteID"`
	UserID     uint      `json:"user_id"`
	User       User      `json:"user" gorm:"foreignKey:UserID"`
	CreatedAt  time.Time `json:"created_at"`
	UpdatedAt  time.Time `json:"updated_at"`
}

type File struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	Name      string    `json:"name"`
	Path      string    `json:"path"`
	Size      int64     `json:"size"`
	MimeType  string    `json:"mime_type"`
	NoteID    *uint     `json:"note_id"`
	Note      *Note     `json:"note,omitempty" gorm:"foreignKey:NoteID"`
	UserID    uint      `json:"user_id"`
	User      User      `json:"user" gorm:"foreignKey:UserID"`
	CreatedAt time.Time `json:"created_at"`
}

type NoteHistory struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	Content   string    `json:"content" gorm:"type:text"`
	Title     string    `json:"title"`
	Action    string    `json:"action"` // created, updated, deleted
	NoteID    uint      `json:"note_id"`
	Note      Note      `json:"note" gorm:"foreignKey:NoteID"`
	UserID    uint      `json:"user_id"`
	User      User      `json:"user" gorm:"foreignKey:UserID"`
	CreatedAt time.Time `json:"created_at"`
}

type Share struct {
	ID         uint       `json:"id" gorm:"primaryKey"`
	ShareToken string     `json:"share_token" gorm:"unique"`
	Permission string     `json:"permission" gorm:"default:'read'"` // read, write
	ExpiresAt  *time.Time `json:"expires_at"`
	IsActive   bool       `json:"is_active" gorm:"default:true"`
	NoteID     uint       `json:"note_id"`
	Note       Note       `json:"note" gorm:"foreignKey:NoteID"`
	OwnerID    uint       `json:"owner_id"`
	Owner      User       `json:"owner" gorm:"foreignKey:OwnerID"`
	SharedWith string     `json:"shared_with"` // email или username
	CreatedAt  time.Time  `json:"created_at"`
	UpdatedAt  time.Time  `json:"updated_at"`
}

type Backup struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	Name      string    `json:"name"`
	Path      string    `json:"path"`
	Size      int64     `json:"size"`
	UserID    uint      `json:"user_id"`
	User      User      `json:"user" gorm:"foreignKey:UserID"`
	CreatedAt time.Time `json:"created_at"`
}

type AuthRequest struct {
	Email    string `json:"email" binding:"required"`
	Password string `json:"password" binding:"required"`
	Username string `json:"username"`
}

type Claims struct {
	UserID uint   `json:"user_id"`
	Email  string `json:"email"`
	jwt.RegisteredClaims
}

type SearchRequest struct {
	Query      string `json:"query"`
	CategoryID *uint  `json:"category_id"`
	TagIDs     []uint `json:"tag_ids"`
	IsArchived *bool  `json:"is_archived"`
	DateFrom   string `json:"date_from"`
	DateTo     string `json:"date_to"`
}

type StatsResponse struct {
	TotalNotes      int64 `json:"total_notes"`
	TotalCategories int64 `json:"total_categories"`
	TotalTags       int64 `json:"total_tags"`
	TotalFiles      int64 `json:"total_files"`
	TotalReminders  int64 `json:"total_reminders"`
	NotesToday      int64 `json:"notes_today"`
	NotesThisWeek   int64 `json:"notes_this_week"`
	NotesThisMonth  int64 `json:"notes_this_month"`
	StorageUsed     int64 `json:"storage_used"`
}

type SyncRequest struct {
	LastSync time.Time `json:"last_sync"`
	DeviceID string    `json:"device_id"`
}

type Link struct {
	ID         uint      `json:"id" gorm:"primaryKey"`
	FromNoteID uint      `json:"from_note_id"`
	FromNote   Note      `json:"from_note" gorm:"foreignKey:FromNoteID"`
	ToNoteID   uint      `json:"to_note_id"`
	ToNote     Note      `json:"to_note" gorm:"foreignKey:ToNoteID"`
	LinkText   string    `json:"link_text"`
	LinkType   string    `json:"link_type" gorm:"default:'reference'"` // reference, embed, mention
	UserID     uint      `json:"user_id"`
	User       User      `json:"user" gorm:"foreignKey:UserID"`
	CreatedAt  time.Time `json:"created_at"`
}

type Canvas struct {
	ID        uint           `json:"id" gorm:"primaryKey"`
	Name      string         `json:"name"`
	Data      datatypes.JSON `json:"data" gorm:"type:text"` // JSON данные canvas
	Width     int            `json:"width" gorm:"default:1920"`
	Height    int            `json:"height" gorm:"default:1080"`
	Zoom      float64        `json:"zoom" gorm:"default:1.0"`
	PanX      float64        `json:"pan_x" gorm:"default:0"`
	PanY      float64        `json:"pan_y" gorm:"default:0"`
	ViewState string         `json:"view_state" gorm:"type:text"` // состояние viewport
	UserID    uint           `json:"user_id"`
	User      User           `json:"user" gorm:"foreignKey:UserID"`
	Nodes     []CanvasNode   `json:"nodes,omitempty" gorm:"foreignKey:CanvasID"`
	CreatedAt time.Time      `json:"created_at"`
	UpdatedAt time.Time      `json:"updated_at"`
}

type CanvasNode struct {
	ID       uint    `json:"id" gorm:"primaryKey"`
	CanvasID uint    `json:"canvas_id"`
	Canvas   Canvas  `json:"-" gorm:"foreignKey:CanvasID"`
	Type     string  `json:"type"` // note, text, image, file, group
	X        float64 `json:"x"`
	Y        float64 `json:"y"`
	Width    float64 `json:"width"`
	Height   float64 `json:"height"`
	Rotation float64 `json:"rotation" gorm:"default:0"`
	Scale    float64 `json:"scale" gorm:"default:1.0"`
	ZIndex   int     `json:"z_index" gorm:"default:0"`
	// простые поля для текста вместо JSON
	Title     string    `json:"title"`
	Content   string    `json:"content"`
	Color     string    `json:"color"`
	NoteID    *uint     `json:"note_id"`
	Note      *Note     `json:"note,omitempty" gorm:"foreignKey:NoteID"`
	UserID    uint      `json:"user_id"`
	User      User      `json:"-" gorm:"foreignKey:UserID"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

type CanvasConnection struct {
	ID         uint       `json:"id" gorm:"primaryKey"`
	CanvasID   uint       `json:"canvas_id"`
	Canvas     Canvas     `json:"-" gorm:"foreignKey:CanvasID"`
	FromNodeID uint       `json:"from_node_id"`
	FromNode   CanvasNode `json:"from_node,omitempty" gorm:"foreignKey:FromNodeID"`
	ToNodeID   uint       `json:"to_node_id"`
	ToNode     CanvasNode `json:"to_node,omitempty" gorm:"foreignKey:ToNodeID"`
	Type       string     `json:"type" gorm:"default:'connection'"`
	UserID     uint       `json:"user_id"`
	User       User       `json:"-" gorm:"foreignKey:UserID"`
	CreatedAt  time.Time  `json:"created_at"`
}

type GraphState struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	Name      string    `json:"name"`
	Data      string    `json:"data" gorm:"type:text"`         // JSON состояние графа
	Layout    string    `json:"layout" gorm:"default:'force'"` // force, circular, tree
	Zoom      float64   `json:"zoom" gorm:"default:1.0"`
	PanX      float64   `json:"pan_x" gorm:"default:0"`
	PanY      float64   `json:"pan_y" gorm:"default:0"`
	Filter    string    `json:"filter" gorm:"type:text"` // фильтры узлов
	UserID    uint      `json:"user_id"`
	User      User      `json:"user" gorm:"foreignKey:UserID"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

type Block struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	BlockID   string    `json:"block_id" gorm:"unique"` // уникальный ID блока
	Content   string    `json:"content" gorm:"type:text"`
	Type      string    `json:"type" gorm:"default:'paragraph'"` // paragraph, heading, list, code, quote
	Level     int       `json:"level" gorm:"default:0"`
	Position  int       `json:"position"`
	NoteID    uint      `json:"note_id"`
	Note      Note      `json:"note" gorm:"foreignKey:NoteID"`
	UserID    uint      `json:"user_id"`
	User      User      `json:"user" gorm:"foreignKey:UserID"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

type Workspace struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	Name      string    `json:"name"`
	Layout    string    `json:"layout" gorm:"type:text"` // JSON layout данные
	IsActive  bool      `json:"is_active" gorm:"default:false"`
	UserID    uint      `json:"user_id"`
	User      User      `json:"user" gorm:"foreignKey:UserID"`
	CreatedAt time.Time `json:"created_at"`
	UpdatedAt time.Time `json:"updated_at"`
}

type DailyNote struct {
	ID        uint      `json:"id" gorm:"primaryKey"`
	Date      string    `json:"date"` // YYYY-MM-DD
	NoteID    uint      `json:"note_id"`
	Note      Note      `json:"note" gorm:"foreignKey:NoteID"`
	UserID    uint      `json:"user_id"`
	User      User      `json:"user" gorm:"foreignKey:UserID"`
	CreatedAt time.Time `json:"created_at"`
}

type Plugin struct {
	ID          uint      `json:"id" gorm:"primaryKey"`
	Name        string    `json:"name"`
	Version     string    `json:"version"`
	Author      string    `json:"author"`
	Description string    `json:"description"`
	Code        string    `json:"code" gorm:"type:text"` // JavaScript код плагина
	IsEnabled   bool      `json:"is_enabled" gorm:"default:false"`
	IsOfficial  bool      `json:"is_official" gorm:"default:false"`
	UserID      uint      `json:"user_id"`
	User        User      `json:"user" gorm:"foreignKey:UserID"`
	CreatedAt   time.Time `json:"created_at"`
	UpdatedAt   time.Time `json:"updated_at"`
}

type Hotkey struct {
	ID          uint   `json:"id" gorm:"primaryKey"`
	Command     string `json:"command"`
	Key         string `json:"key"`
	Modifiers   string `json:"modifiers"` // ctrl, alt, shift
	Description string `json:"description"`
	UserID      uint   `json:"user_id"`
	User        User   `json:"user" gorm:"foreignKey:UserID"`
}

type GraphNode struct {
	ID    string  `json:"id"`
	Label string  `json:"label"`
	Type  string  `json:"type"` // note, tag, category
	Color string  `json:"color"`
	Size  int     `json:"size"`
	X     float64 `json:"x"`
	Y     float64 `json:"y"`
}

type GraphEdge struct {
	Source string `json:"source"`
	Target string `json:"target"`
	Type   string `json:"type"`
	Weight int    `json:"weight"`
}

type GraphData struct {
	Nodes []GraphNode `json:"nodes"`
	Edges []GraphEdge `json:"edges"`
}

func initDB() {
	var err error
	db, err = gorm.Open(sqlite.Open("redeluxe.db?_pragma=encoding=UTF-8"), &gorm.Config{})
	if err != nil {
		panic("не удалось подключиться к базе данных")
	}

	// устанавливаем UTF-8 кодировку для SQLite
	db.Exec("PRAGMA encoding = 'UTF-8'")

	db.AutoMigrate(&User{}, &Category{}, &Tag{}, &Note{}, &Template{}, &Reminder{}, &File{}, &NoteHistory{}, &Share{}, &Backup{}, &Link{}, &Canvas{}, &CanvasNode{}, &CanvasConnection{}, &GraphState{}, &Block{}, &Workspace{}, &DailyNote{}, &Plugin{}, &Hotkey{})
}

func generateToken(userID uint, email string) (string, error) {
	claims := &Claims{
		UserID: userID,
		Email:  email,
		RegisteredClaims: jwt.RegisteredClaims{
			ExpiresAt: jwt.NewNumericDate(time.Now().Add(24 * time.Hour)),
		},
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString(jwtSecret)
}

func authMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		tokenString := c.GetHeader("Authorization")
		if tokenString == "" {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "токен отсутствует"})
			c.Abort()
			return
		}

		if len(tokenString) > 7 && tokenString[:7] == "Bearer " {
			tokenString = tokenString[7:]
		}

		token, err := jwt.ParseWithClaims(tokenString, &Claims{}, func(token *jwt.Token) (interface{}, error) {
			return jwtSecret, nil
		})

		if err != nil || !token.Valid {
			c.JSON(http.StatusUnauthorized, gin.H{"error": "неверный токен"})
			c.Abort()
			return
		}

		if claims, ok := token.Claims.(*Claims); ok {
			c.Set("user_id", claims.UserID)
			c.Set("email", claims.Email)
		}

		c.Next()
	}
}

func register(c *gin.Context) {
	var req AuthRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	hashedPassword, err := bcrypt.GenerateFromPassword([]byte(req.Password), bcrypt.DefaultCost)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "ошибка хеширования пароля"})
		return
	}

	user := User{
		Email:    req.Email,
		Password: string(hashedPassword),
		Username: req.Username,
	}

	if err := db.Create(&user).Error; err != nil {
		c.JSON(http.StatusConflict, gin.H{"error": "пользователь уже существует"})
		return
	}

	token, err := generateToken(user.ID, user.Email)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "ошибка создания токена"})
		return
	}

	c.JSON(http.StatusCreated, gin.H{
		"token": token,
		"user": gin.H{
			"id":       user.ID,
			"email":    user.Email,
			"username": user.Username,
		},
	})
}

func login(c *gin.Context) {
	var req AuthRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	var user User
	if err := db.Where("email = ?", req.Email).First(&user).Error; err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "неверный email или пароль"})
		return
	}

	if err := bcrypt.CompareHashAndPassword([]byte(user.Password), []byte(req.Password)); err != nil {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "неверный email или пароль"})
		return
	}

	token, err := generateToken(user.ID, user.Email)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "ошибка создания токена"})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"token": token,
		"user": gin.H{
			"id":       user.ID,
			"email":    user.Email,
			"username": user.Username,
		},
	})
}

func getNotes(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var query = db.Where("user_id = ?", userID)

	if archived := c.Query("archived"); archived != "" {
		if archived == "true" {
			query = query.Where("is_archived = ?", true)
		} else {
			query = query.Where("is_archived = ?", false)
		}
	}

	if pinned := c.Query("pinned"); pinned == "true" {
		query = query.Where("is_pinned = ?", true)
	}

	if categoryID := c.Query("category_id"); categoryID != "" {
		query = query.Where("category_id = ?", categoryID)
	}

	var notes []Note
	query.Preload("Category").Preload("Tags").Order("is_pinned desc, updated_at desc").Find(&notes)

	c.JSON(http.StatusOK, gin.H{"notes": notes})
}

func logNoteHistory(noteID uint, userID uint, action string, title string, content string) {
	history := NoteHistory{
		NoteID:  noteID,
		UserID:  userID,
		Action:  action,
		Title:   title,
		Content: content,
	}
	db.Create(&history)
}

func createNote(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var note Note
	if err := c.ShouldBindJSON(&note); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	note.UserID = userID
	if note.Color == "" {
		note.Color = "#00ffff"
	}

	db.Create(&note)

	logNoteHistory(note.ID, userID, "created", note.Title, note.Content)

	db.Preload("Category").Preload("Tags").First(&note, note.ID)
	c.JSON(http.StatusCreated, note)
}

func updateNote(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	noteID := c.Param("id")

	var note Note
	if err := db.Preload("Tags").Where("id = ? AND user_id = ?", noteID, userID).First(&note).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "заметка не найдена"})
		return
	}

	oldTitle := note.Title
	oldContent := note.Content

	var updateData map[string]interface{}
	if err := c.ShouldBindJSON(&updateData); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	if tagIDs, ok := updateData["tag_ids"].([]interface{}); ok {
		var tags []Tag
		for _, tagID := range tagIDs {
			if id, ok := tagID.(float64); ok {
				var tag Tag
				if err := db.Where("id = ? AND user_id = ?", uint(id), userID).First(&tag).Error; err == nil {
					tags = append(tags, tag)
				}
			}
		}
		db.Model(&note).Association("Tags").Replace(tags)
		delete(updateData, "tag_ids")
	}

	db.Model(&note).Updates(updateData)

	var newTitle, newContent string
	if title, ok := updateData["title"].(string); ok {
		newTitle = title
	} else {
		newTitle = oldTitle
	}
	if content, ok := updateData["content"].(string); ok {
		newContent = content
	} else {
		newContent = oldContent
	}

	if newTitle != oldTitle || newContent != oldContent {
		logNoteHistory(note.ID, userID, "updated", newTitle, newContent)
	}

	db.Preload("Category").Preload("Tags").First(&note, note.ID)
	c.JSON(http.StatusOK, note)
}

func deleteNote(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	noteID := c.Param("id")

	var note Note
	if err := db.Where("id = ? AND user_id = ?", noteID, userID).First(&note).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "заметка не найдена"})
		return
	}

	// логируем удаление
	logNoteHistory(note.ID, userID, "deleted", note.Title, note.Content)

	result := db.Where("id = ? AND user_id = ?", noteID, userID).Delete(&Note{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "заметка не найдена"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "заметка удалена"})
}

func getCategories(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var categories []Category
	db.Where("user_id = ?", userID).Order("name").Find(&categories)

	c.JSON(http.StatusOK, gin.H{"categories": categories})
}

func createCategory(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var category Category
	if err := c.ShouldBindJSON(&category); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	category.UserID = userID
	if category.Color == "" {
		category.Color = "#00ffff"
	}

	db.Create(&category)
	c.JSON(http.StatusCreated, category)
}

func updateCategory(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	categoryID := c.Param("id")

	var category Category
	if err := db.Where("id = ? AND user_id = ?", categoryID, userID).First(&category).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "категория не найдена"})
		return
	}

	if err := c.ShouldBindJSON(&category); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	db.Save(&category)
	c.JSON(http.StatusOK, category)
}

func deleteCategory(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	categoryID := c.Param("id")

	result := db.Where("id = ? AND user_id = ?", categoryID, userID).Delete(&Category{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "категория не найдена"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "категория удалена"})
}

func getTags(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var tags []Tag
	db.Where("user_id = ?", userID).Order("name").Find(&tags)

	c.JSON(http.StatusOK, gin.H{"tags": tags})
}

func createTag(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var tag Tag
	if err := c.ShouldBindJSON(&tag); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	tag.UserID = userID
	if tag.Color == "" {
		tag.Color = "#ff0080"
	}

	db.Create(&tag)
	c.JSON(http.StatusCreated, tag)
}

func deleteTag(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	tagID := c.Param("id")

	result := db.Where("id = ? AND user_id = ?", tagID, userID).Delete(&Tag{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "тег не найден"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "тег удален"})
}

func searchNotes(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	query := c.Query("q")

	if query == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "запрос не может быть пустым"})
		return
	}

	var notes []Note
	searchQuery := db.Where("user_id = ?", userID).
		Where("(title LIKE ? OR content LIKE ?)", "%"+query+"%", "%"+query+"%").
		Preload("Category").
		Preload("Tags").
		Order("updated_at desc")

	searchQuery.Find(&notes)

	c.JSON(http.StatusOK, gin.H{"notes": notes, "query": query})
}

func getStats(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var stats StatsResponse

	// общая статистика
	db.Model(&Note{}).Where("user_id = ?", userID).Count(&stats.TotalNotes)
	db.Model(&Category{}).Where("user_id = ?", userID).Count(&stats.TotalCategories)
	db.Model(&Tag{}).Where("user_id = ?", userID).Count(&stats.TotalTags)
	db.Model(&File{}).Where("user_id = ?", userID).Count(&stats.TotalFiles)
	db.Model(&Reminder{}).Where("user_id = ?", userID).Count(&stats.TotalReminders)

	// статистика по времени
	now := time.Now()
	today := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, now.Location())
	weekAgo := today.AddDate(0, 0, -7)
	monthAgo := today.AddDate(0, -1, 0)

	db.Model(&Note{}).Where("user_id = ? AND created_at >= ?", userID, today).Count(&stats.NotesToday)
	db.Model(&Note{}).Where("user_id = ? AND created_at >= ?", userID, weekAgo).Count(&stats.NotesThisWeek)
	db.Model(&Note{}).Where("user_id = ? AND created_at >= ?", userID, monthAgo).Count(&stats.NotesThisMonth)

	// статистика использования хранилища
	var totalSize int64
	db.Model(&File{}).Where("user_id = ?", userID).Select("COALESCE(SUM(size), 0)").Scan(&totalSize)
	stats.StorageUsed = totalSize

	c.JSON(http.StatusOK, stats)
}

func exportNotes(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	format := c.Query("format")

	if format == "" {
		format = "json"
	}

	var notes []Note
	db.Where("user_id = ?", userID).
		Preload("Category").
		Preload("Tags").
		Order("created_at desc").
		Find(&notes)

	switch format {
	case "json":
		c.Header("Content-Disposition", "attachment; filename=notes.json")
		c.JSON(http.StatusOK, gin.H{"notes": notes, "exported_at": time.Now()})
	case "txt":
		c.Header("Content-Type", "text/plain")
		c.Header("Content-Disposition", "attachment; filename=notes.txt")

		var result strings.Builder
		result.WriteString("REDELUXE NOTES EXPORT\n")
		result.WriteString("====================\n\n")

		for _, note := range notes {
			result.WriteString(fmt.Sprintf("TITLE: %s\n", note.Title))
			result.WriteString(fmt.Sprintf("CREATED: %s\n", note.CreatedAt.Format("2006-01-02 15:04:05")))
			if note.Category != nil {
				result.WriteString(fmt.Sprintf("CATEGORY: %s\n", note.Category.Name))
			}
			if len(note.Tags) > 0 {
				var tagNames []string
				for _, tag := range note.Tags {
					tagNames = append(tagNames, tag.Name)
				}
				result.WriteString(fmt.Sprintf("TAGS: %s\n", strings.Join(tagNames, ", ")))
			}
			result.WriteString(fmt.Sprintf("CONTENT:\n%s\n\n", note.Content))
			result.WriteString("---\n\n")
		}

		c.String(http.StatusOK, result.String())
	default:
		c.JSON(http.StatusBadRequest, gin.H{"error": "неподдерживаемый формат"})
	}
}

func getTemplates(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var templates []Template
	query := db.Where("user_id = ? OR is_public = ?", userID, true)

	if category := c.Query("category"); category != "" {
		query = query.Where("category = ?", category)
	}

	query.Order("name").Find(&templates)
	c.JSON(http.StatusOK, gin.H{"templates": templates})
}

func createTemplate(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var template Template
	if err := c.ShouldBindJSON(&template); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	template.UserID = userID
	db.Create(&template)
	c.JSON(http.StatusCreated, template)
}

func deleteTemplate(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	templateID := c.Param("id")

	result := db.Where("id = ? AND user_id = ?", templateID, userID).Delete(&Template{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "шаблон не найден"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "шаблон удален"})
}

func getReminders(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var reminders []Reminder
	query := db.Where("user_id = ?", userID)

	if active := c.Query("active"); active == "true" {
		query = query.Where("is_active = ?", true)
	}

	query.Preload("Note").Order("date_time").Find(&reminders)
	c.JSON(http.StatusOK, gin.H{"reminders": reminders})
}

func createReminder(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var reminder Reminder
	if err := c.ShouldBindJSON(&reminder); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	reminder.UserID = userID
	db.Create(&reminder)
	c.JSON(http.StatusCreated, reminder)
}

func updateReminder(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	reminderID := c.Param("id")

	var reminder Reminder
	if err := db.Where("id = ? AND user_id = ?", reminderID, userID).First(&reminder).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "напоминание не найдено"})
		return
	}

	if err := c.ShouldBindJSON(&reminder); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	db.Save(&reminder)
	c.JSON(http.StatusOK, reminder)
}

func deleteReminder(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	reminderID := c.Param("id")

	result := db.Where("id = ? AND user_id = ?", reminderID, userID).Delete(&Reminder{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "напоминание не найдено"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "напоминание удалено"})
}

func uploadFile(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	file, header, err := c.Request.FormFile("file")
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "файл не найден"})
		return
	}
	defer file.Close()

	// создаем папку пользователя
	userDir := fmt.Sprintf("uploads/%d", userID)
	os.MkdirAll(userDir, 0755)

	// генерируем уникальное имя файла
	filename := fmt.Sprintf("%d_%s", time.Now().Unix(), header.Filename)
	filepath := filepath.Join(userDir, filename)

	// сохраняем файл
	out, err := os.Create(filepath)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "ошибка сохранения файла"})
		return
	}
	defer out.Close()

	size, err := io.Copy(out, file)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "ошибка записи файла"})
		return
	}

	// сохраняем в базу
	fileRecord := File{
		Name:     header.Filename,
		Path:     filepath,
		Size:     size,
		MimeType: header.Header.Get("Content-Type"),
		UserID:   userID,
	}

	if noteID := c.PostForm("note_id"); noteID != "" {
		if id, err := strconv.Atoi(noteID); err == nil {
			noteIDPtr := uint(id)
			fileRecord.NoteID = &noteIDPtr
		}
	}

	db.Create(&fileRecord)
	c.JSON(http.StatusCreated, fileRecord)
}

func getFiles(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var files []File
	query := db.Where("user_id = ?", userID)

	if noteID := c.Query("note_id"); noteID != "" {
		query = query.Where("note_id = ?", noteID)
	}

	query.Order("created_at desc").Find(&files)
	c.JSON(http.StatusOK, gin.H{"files": files})
}

func deleteFile(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	fileID := c.Param("id")

	var file File
	if err := db.Where("id = ? AND user_id = ?", fileID, userID).First(&file).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "файл не найден"})
		return
	}

	// удаляем физический файл
	os.Remove(file.Path)

	// удаляем запись из базы
	db.Delete(&file)
	c.JSON(http.StatusOK, gin.H{"message": "файл удален"})
}

func getNoteHistory(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	noteID := c.Param("id")

	var history []NoteHistory
	db.Where("user_id = ? AND note_id = ?", userID, noteID).Order("created_at desc").Find(&history)
	c.JSON(http.StatusOK, gin.H{"history": history})
}

func shareNote(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	noteID := c.Param("id")

	var note Note
	if err := db.Where("id = ? AND user_id = ?", noteID, userID).First(&note).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "заметка не найдена"})
		return
	}

	var shareData map[string]interface{}
	if err := c.ShouldBindJSON(&shareData); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	// генерируем токен
	tokenBytes := make([]byte, 32)
	rand.Read(tokenBytes)
	token := base64.URLEncoding.EncodeToString(tokenBytes)

	share := Share{
		ShareToken: token,
		NoteID:     uint(note.ID),
		OwnerID:    userID,
		Permission: shareData["permission"].(string),
		SharedWith: shareData["shared_with"].(string),
	}

	if expiresAt, ok := shareData["expires_at"].(string); ok && expiresAt != "" {
		if t, err := time.Parse(time.RFC3339, expiresAt); err == nil {
			share.ExpiresAt = &t
		}
	}

	db.Create(&share)
	c.JSON(http.StatusCreated, gin.H{"share_token": token, "share": share})
}

func getSharedNote(c *gin.Context) {
	token := c.Param("token")

	var share Share
	if err := db.Where("share_token = ? AND is_active = ?", token, true).Preload("Note.Category").Preload("Note.Tags").First(&share).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "ссылка недействительна"})
		return
	}

	// проверяем срок действия
	if share.ExpiresAt != nil && share.ExpiresAt.Before(time.Now()) {
		c.JSON(http.StatusForbidden, gin.H{"error": "ссылка истекла"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"note": share.Note, "permission": share.Permission})
}

func getMyShares(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var shares []Share
	db.Where("owner_id = ?", userID).Preload("Note").Order("created_at desc").Find(&shares)
	c.JSON(http.StatusOK, gin.H{"shares": shares})
}

// backup и restore
func createBackup(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	// экспортируем все данные пользователя
	var notes []Note
	var categories []Category
	var tags []Tag
	var templates []Template
	var reminders []Reminder

	db.Where("user_id = ?", userID).Preload("Category").Preload("Tags").Find(&notes)
	db.Where("user_id = ?", userID).Find(&categories)
	db.Where("user_id = ?", userID).Find(&tags)
	db.Where("user_id = ?", userID).Find(&templates)
	db.Where("user_id = ?", userID).Find(&reminders)

	backupData := map[string]interface{}{
		"notes":      notes,
		"categories": categories,
		"tags":       tags,
		"templates":  templates,
		"reminders":  reminders,
		"created_at": time.Now(),
	}

	// сохраняем в файл
	backupDir := "backups"
	os.MkdirAll(backupDir, 0755)

	filename := fmt.Sprintf("backup_%d_%d.json", userID, time.Now().Unix())
	filepath := filepath.Join(backupDir, filename)

	jsonData, _ := json.MarshalIndent(backupData, "", "  ")
	err := os.WriteFile(filepath, jsonData, 0644)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "ошибка создания backup"})
		return
	}

	// сохраняем запись в базу
	backup := Backup{
		Name:   filename,
		Path:   filepath,
		Size:   int64(len(jsonData)),
		UserID: userID,
	}

	db.Create(&backup)
	c.JSON(http.StatusCreated, gin.H{"backup": backup, "download_url": "/api/backup/" + strconv.Itoa(int(backup.ID))})
}

func getBackups(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var backups []Backup
	db.Where("user_id = ?", userID).Order("created_at desc").Find(&backups)
	c.JSON(http.StatusOK, gin.H{"backups": backups})
}

func downloadBackup(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	backupID := c.Param("id")

	var backup Backup
	if err := db.Where("id = ? AND user_id = ?", backupID, userID).First(&backup).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "backup не найден"})
		return
	}

	c.Header("Content-Disposition", "attachment; filename="+backup.Name)
	c.File(backup.Path)
}

// синхронизация
func syncData(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var syncReq SyncRequest
	if err := c.ShouldBindJSON(&syncReq); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	// получаем изменения после lastSync
	var notes []Note
	var categories []Category
	var tags []Tag

	db.Where("user_id = ? AND updated_at > ?", userID, syncReq.LastSync).Preload("Category").Preload("Tags").Find(&notes)
	db.Where("user_id = ? AND updated_at > ?", userID, syncReq.LastSync).Find(&categories)
	db.Where("user_id = ? AND created_at > ?", userID, syncReq.LastSync).Find(&tags)

	syncData := map[string]interface{}{
		"notes":       notes,
		"categories":  categories,
		"tags":        tags,
		"server_time": time.Now(),
	}

	c.JSON(http.StatusOK, syncData)
}

// продвинутый поиск
func advancedSearch(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var searchReq SearchRequest
	if err := c.ShouldBindJSON(&searchReq); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	query := db.Where("user_id = ?", userID)

	// текстовый поиск
	if searchReq.Query != "" {
		query = query.Where("(title LIKE ? OR content LIKE ?)", "%"+searchReq.Query+"%", "%"+searchReq.Query+"%")
	}

	// фильтр по категории
	if searchReq.CategoryID != nil {
		query = query.Where("category_id = ?", *searchReq.CategoryID)
	}

	// фильтр по архивным заметкам
	if searchReq.IsArchived != nil {
		query = query.Where("is_archived = ?", *searchReq.IsArchived)
	}

	// фильтр по датам
	if searchReq.DateFrom != "" {
		if date, err := time.Parse("2006-01-02", searchReq.DateFrom); err == nil {
			query = query.Where("created_at >= ?", date)
		}
	}
	if searchReq.DateTo != "" {
		if date, err := time.Parse("2006-01-02", searchReq.DateTo); err == nil {
			query = query.Where("created_at <= ?", date.Add(24*time.Hour))
		}
	}

	var notes []Note
	query.Preload("Category").Preload("Tags").Order("updated_at desc").Find(&notes)

	// фильтр по тегам (после загрузки)
	if len(searchReq.TagIDs) > 0 {
		var filteredNotes []Note
		for _, note := range notes {
			hasTag := false
			for _, tag := range note.Tags {
				for _, tagID := range searchReq.TagIDs {
					if tag.ID == tagID {
						hasTag = true
						break
					}
				}
				if hasTag {
					break
				}
			}
			if hasTag {
				filteredNotes = append(filteredNotes, note)
			}
		}
		notes = filteredNotes
	}

	c.JSON(http.StatusOK, gin.H{"notes": notes, "total": len(notes)})
}

// graph view - граф связей
func getGraph(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var notes []Note
	var links []Link
	var categories []Category
	var tags []Tag

	// фильтры
	categoryFilter := c.Query("category")
	tagFilter := c.Query("tag")
	typeFilter := c.Query("type") // note, category, tag

	query := db.Where("user_id = ?", userID)
	if categoryFilter != "" {
		query = query.Where("category_id = ?", categoryFilter)
	}

	query.Preload("Category").Preload("Tags").Find(&notes)
	db.Where("user_id = ?", userID).Preload("FromNote").Preload("ToNote").Find(&links)
	db.Where("user_id = ?", userID).Find(&categories)
	db.Where("user_id = ?", userID).Find(&tags)

	graph := GraphData{
		Nodes: make([]GraphNode, 0),
		Edges: make([]GraphEdge, 0),
	}

	// добавляем заметки как узлы
	if typeFilter == "" || typeFilter == "note" {
		for _, note := range notes {
			size := len(note.Content)/10 + 10
			if size > 50 {
				size = 50
			}

			graph.Nodes = append(graph.Nodes, GraphNode{
				ID:    fmt.Sprintf("note_%d", note.ID),
				Label: note.Title,
				Type:  "note",
				Color: note.Color,
				Size:  size,
			})
		}
	}

	// добавляем категории как узлы
	if typeFilter == "" || typeFilter == "category" {
		for _, cat := range categories {
			graph.Nodes = append(graph.Nodes, GraphNode{
				ID:    fmt.Sprintf("cat_%d", cat.ID),
				Label: cat.Name,
				Type:  "category",
				Color: cat.Color,
				Size:  20,
			})
		}
	}

	// добавляем теги как узлы
	if typeFilter == "" || typeFilter == "tag" {
		for _, tag := range tags {
			if tagFilter == "" || tag.Name == tagFilter {
				graph.Nodes = append(graph.Nodes, GraphNode{
					ID:    fmt.Sprintf("tag_%d", tag.ID),
					Label: tag.Name,
					Type:  "tag",
					Color: tag.Color,
					Size:  15,
				})
			}
		}
	}

	// добавляем связи
	for _, link := range links {
		graph.Edges = append(graph.Edges, GraphEdge{
			Source: fmt.Sprintf("note_%d", link.FromNoteID),
			Target: fmt.Sprintf("note_%d", link.ToNoteID),
			Type:   link.LinkType,
			Weight: 1,
		})
	}

	// связи заметок с категориями
	for _, note := range notes {
		if note.Category != nil {
			graph.Edges = append(graph.Edges, GraphEdge{
				Source: fmt.Sprintf("note_%d", note.ID),
				Target: fmt.Sprintf("cat_%d", note.Category.ID),
				Type:   "category",
				Weight: 1,
			})
		}
	}

	// связи заметок с тегами
	for _, note := range notes {
		for _, tag := range note.Tags {
			if tagFilter == "" || tag.Name == tagFilter {
				graph.Edges = append(graph.Edges, GraphEdge{
					Source: fmt.Sprintf("note_%d", note.ID),
					Target: fmt.Sprintf("tag_%d", tag.ID),
					Type:   "tag",
					Weight: 1,
				})
			}
		}
	}

	c.JSON(http.StatusOK, graph)
}

// связи между заметками
func createLink(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var link Link
	if err := c.ShouldBindJSON(&link); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	link.UserID = userID
	db.Create(&link)
	c.JSON(http.StatusCreated, link)
}

func getBacklinks(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	noteID := c.Param("id")

	var links []Link
	db.Where("user_id = ? AND to_note_id = ?", userID, noteID).Preload("FromNote").Find(&links)
	c.JSON(http.StatusOK, gin.H{"backlinks": links})
}

func saveGraphState(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var graphState GraphState
	if err := c.ShouldBindJSON(&graphState); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	graphState.UserID = userID

	// ищем существующее состояние для этого пользователя
	var existing GraphState
	result := db.Where("user_id = ? AND name = ?", userID, graphState.Name).First(&existing)

	if result.Error == nil {
		// обновляем существующее
		existing.Data = graphState.Data
		existing.Layout = graphState.Layout
		existing.Zoom = graphState.Zoom
		existing.PanX = graphState.PanX
		existing.PanY = graphState.PanY
		existing.Filter = graphState.Filter
		db.Save(&existing)
		c.JSON(http.StatusOK, existing)
	} else {
		// создаем новое
		db.Create(&graphState)
		c.JSON(http.StatusCreated, graphState)
	}
}

func getGraphStates(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var states []GraphState
	db.Where("user_id = ?", userID).Order("updated_at desc").Find(&states)
	c.JSON(http.StatusOK, gin.H{"states": states})
}

func getGraphState(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	stateID := c.Param("id")

	var state GraphState
	if err := db.Where("id = ? AND user_id = ?", stateID, userID).First(&state).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "состояние не найдено"})
		return
	}

	c.JSON(http.StatusOK, state)
}

func deleteGraphState(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	stateID := c.Param("id")

	result := db.Where("id = ? AND user_id = ?", stateID, userID).Delete(&GraphState{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "состояние не найдено"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "состояние удалено"})
}

func autoSaveCanvasState(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	canvasID := c.Param("id")

	var stateData struct {
		Zoom      float64 `json:"zoom"`
		PanX      float64 `json:"pan_x"`
		PanY      float64 `json:"pan_y"`
		ViewState string  `json:"view_state"`
	}

	if err := c.ShouldBindJSON(&stateData); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	// быстрое обновление только viewport состояния
	result := db.Model(&Canvas{}).
		Where("id = ? AND user_id = ?", canvasID, userID).
		Updates(map[string]interface{}{
			"zoom":       stateData.Zoom,
			"pan_x":      stateData.PanX,
			"pan_y":      stateData.PanY,
			"view_state": stateData.ViewState,
		})

	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "canvas не найден"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "автосохранение выполнено"})
}

// canvas - визуальные заметки
func getCanvases(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var canvases []Canvas
	db.Where("user_id = ?", userID).Order("updated_at desc").Find(&canvases)
	c.JSON(http.StatusOK, gin.H{"canvases": canvases})
}

func createCanvas(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var canvas Canvas
	if err := c.ShouldBindJSON(&canvas); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	canvas.UserID = userID
	db.Create(&canvas)
	c.JSON(http.StatusCreated, canvas)
}

func updateCanvas(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	canvasID := c.Param("id")

	var canvas Canvas
	if err := db.Where("id = ? AND user_id = ?", canvasID, userID).First(&canvas).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "canvas не найден"})
		return
	}

	var updateData map[string]interface{}
	if err := c.ShouldBindJSON(&updateData); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	// если передаются узлы, обновляем их отдельно
	if nodesData, ok := updateData["nodes"]; ok {
		tx := db.Begin()
		if err := tx.Where("canvas_id = ?", canvasID).Delete(&CanvasNode{}).Error; err != nil {
			tx.Rollback()
			c.JSON(http.StatusInternalServerError, gin.H{"error": "ошибка удаления старых узлов"})
			return
		}

		nodesJSON, _ := json.Marshal(nodesData)
		var nodes []CanvasNode
		json.Unmarshal(nodesJSON, &nodes)

		for _, node := range nodes {
			node.CanvasID = canvas.ID
			node.UserID = userID
			if err := tx.Create(&node).Error; err != nil {
				tx.Rollback()
				c.JSON(http.StatusInternalServerError, gin.H{"error": "ошибка сохранения узла"})
				return
			}
		}
		tx.Commit()
		delete(updateData, "nodes") // удаляем узлы из основного обновления
	}

	db.Model(&canvas).Updates(updateData)
	c.JSON(http.StatusOK, canvas)
}

func saveCanvasState(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	canvasID := c.Param("id")

	var canvas Canvas
	if err := db.Where("id = ? AND user_id = ?", canvasID, userID).First(&canvas).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "canvas не найден"})
		return
	}

	var stateData struct {
		Zoom        float64            `json:"zoom"`
		PanX        float64            `json:"pan_x"`
		PanY        float64            `json:"pan_y"`
		ViewState   string             `json:"view_state"`
		Data        string             `json:"data"`
		Nodes       []CanvasNode       `json:"nodes"`
		Connections []CanvasConnection `json:"connections"`
	}

	if err := c.ShouldBindJSON(&stateData); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	// начинаем транзакцию
	tx := db.Begin()

	// обновляем canvas
	canvas.Zoom = stateData.Zoom
	canvas.PanX = stateData.PanX
	canvas.PanY = stateData.PanY
	canvas.ViewState = stateData.ViewState
	if stateData.Data != "" {
		canvas.Data = datatypes.JSON(stateData.Data)
	}
	if err := tx.Save(&canvas).Error; err != nil {
		tx.Rollback()
		c.JSON(http.StatusInternalServerError, gin.H{"error": "ошибка сохранения canvas"})
		return
	}

	// удаляем старые узлы и связи
	if err := tx.Where("canvas_id = ?", canvasID).Delete(&CanvasNode{}).Error; err != nil {
		tx.Rollback()
		c.JSON(http.StatusInternalServerError, gin.H{"error": "ошибка удаления старых узлов"})
		return
	}
	if err := tx.Where("canvas_id = ?", canvasID).Delete(&CanvasConnection{}).Error; err != nil {
		tx.Rollback()
		c.JSON(http.StatusInternalServerError, gin.H{"error": "ошибка удаления старых связей"})
		return
	}

	// сохраняем узлы
	nodeIdMap := make(map[string]uint) // для связей
	if len(stateData.Nodes) > 0 {
		for i, node := range stateData.Nodes {
			node.CanvasID = canvas.ID
			node.UserID = userID
			if err := tx.Create(&node).Error; err != nil {
				tx.Rollback()
				c.JSON(http.StatusInternalServerError, gin.H{"error": "ошибка сохранения узла"})
				return
			}
			// сохраняем соответствие старый индекс -> новый ID
			nodeIdMap[fmt.Sprintf("%d", i)] = node.ID
		}
	}

	// сохраняем связи
	if len(stateData.Connections) > 0 {
		for _, conn := range stateData.Connections {
			conn.CanvasID = canvas.ID
			conn.UserID = userID
			if err := tx.Create(&conn).Error; err != nil {
				tx.Rollback()
				c.JSON(http.StatusInternalServerError, gin.H{"error": "ошибка сохранения связи"})
				return
			}
		}
	}

	tx.Commit()
	c.JSON(http.StatusOK, gin.H{"message": "состояние сохранено"})
}

func createCanvasNode(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	canvasID := c.Param("id")

	var node CanvasNode
	if err := c.ShouldBindJSON(&node); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	// проверяем что canvas принадлежит пользователю
	var canvas Canvas
	if err := db.Where("id = ? AND user_id = ?", canvasID, userID).First(&canvas).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "canvas не найден"})
		return
	}

	node.CanvasID = canvas.ID
	node.UserID = userID
	db.Create(&node)
	c.JSON(http.StatusCreated, node)
}

func updateCanvasNode(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	nodeID := c.Param("nodeId")

	var node CanvasNode
	if err := db.Where("id = ? AND user_id = ?", nodeID, userID).First(&node).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "узел не найден"})
		return
	}

	var updateData map[string]interface{}
	if err := c.ShouldBindJSON(&updateData); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	db.Model(&node).Updates(updateData)
	c.JSON(http.StatusOK, node)
}

func deleteCanvasNode(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	nodeID := c.Param("nodeId")

	result := db.Where("id = ? AND user_id = ?", nodeID, userID).Delete(&CanvasNode{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "узел не найден"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "узел удален"})
}

func getCanvas(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	canvasID := c.Param("id")

	var canvas Canvas
	if err := db.Where("id = ? AND user_id = ?", canvasID, userID).Preload("Nodes.Note").First(&canvas).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "canvas не найден"})
		return
	}

	// загружаем связи
	var connections []CanvasConnection
	db.Where("canvas_id = ?", canvasID).Preload("FromNode").Preload("ToNode").Find(&connections)

	// формируем ответ с узлами и связями
	response := map[string]interface{}{
		"id":          canvas.ID,
		"name":        canvas.Name,
		"data":        canvas.Data,
		"width":       canvas.Width,
		"height":      canvas.Height,
		"zoom":        canvas.Zoom,
		"pan_x":       canvas.PanX,
		"pan_y":       canvas.PanY,
		"view_state":  canvas.ViewState,
		"nodes":       canvas.Nodes,
		"connections": connections,
		"created_at":  canvas.CreatedAt,
		"updated_at":  canvas.UpdatedAt,
	}

	c.JSON(http.StatusOK, response)
}

func deleteCanvas(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	canvasID := c.Param("id")

	// удаляем все узлы canvas
	db.Where("canvas_id = ? AND user_id = ?", canvasID, userID).Delete(&CanvasNode{})

	// удаляем сам canvas
	result := db.Where("id = ? AND user_id = ?", canvasID, userID).Delete(&Canvas{})
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "canvas не найден"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "canvas удален"})
}

// ежедневные заметки
func getDailyNote(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	date := c.Query("date")

	if date == "" {
		date = time.Now().Format("2006-01-02")
	}

	var dailyNote DailyNote
	if err := db.Where("user_id = ? AND date = ?", userID, date).Preload("Note").First(&dailyNote).Error; err != nil {
		note := Note{
			Title:   fmt.Sprintf("Daily Note - %s", date),
			Content: fmt.Sprintf("# %s\n\n## задачи\n- [ ] \n\n## заметки\n\n## рефлексия\n", date),
			Color:   "#00ffff",
			UserID:  userID,
		}
		db.Create(&note)

		dailyNote = DailyNote{
			Date:   date,
			NoteID: note.ID,
			Note:   note,
			UserID: userID,
		}
		db.Create(&dailyNote)
	}

	c.JSON(http.StatusOK, dailyNote)
}

// блоки заметок
func getNoteBlocks(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	noteID := c.Param("id")

	var blocks []Block
	db.Where("user_id = ? AND note_id = ?", userID, noteID).Order("position").Find(&blocks)
	c.JSON(http.StatusOK, gin.H{"blocks": blocks})
}

func updateNoteBlocks(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	noteID := c.Param("id")

	var blocks []Block
	if err := c.ShouldBindJSON(&blocks); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	// удаляем старые блоки
	db.Where("user_id = ? AND note_id = ?", userID, noteID).Delete(&Block{})

	// создаем новые блоки
	for i, block := range blocks {
		block.UserID = userID
		block.NoteID = uint(atoi(noteID))
		block.Position = i
		if block.BlockID == "" {
			block.BlockID = generateBlockID()
		}
		db.Create(&block)
	}

	c.JSON(http.StatusOK, gin.H{"blocks": blocks})
}

// рабочие пространства
func getWorkspaces(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var workspaces []Workspace
	db.Where("user_id = ?", userID).Order("name").Find(&workspaces)
	c.JSON(http.StatusOK, gin.H{"workspaces": workspaces})
}

func createWorkspace(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var workspace Workspace
	if err := c.ShouldBindJSON(&workspace); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	workspace.UserID = userID
	db.Create(&workspace)
	c.JSON(http.StatusCreated, workspace)
}

func activateWorkspace(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	workspaceID := c.Param("id")

	// деактивируем все workspace
	db.Model(&Workspace{}).Where("user_id = ?", userID).Update("is_active", false)

	// активируем выбранный
	result := db.Model(&Workspace{}).Where("id = ? AND user_id = ?", workspaceID, userID).Update("is_active", true)
	if result.RowsAffected == 0 {
		c.JSON(http.StatusNotFound, gin.H{"error": "workspace не найден"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"message": "workspace активирован"})
}

// плагины
func getPlugins(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var plugins []Plugin
	query := db.Where("user_id = ? OR is_official = ?", userID, true)
	query.Order("is_official desc, name").Find(&plugins)
	c.JSON(http.StatusOK, gin.H{"plugins": plugins})
}

func togglePlugin(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)
	pluginID := c.Param("id")

	var plugin Plugin
	if err := db.Where("id = ? AND (user_id = ? OR is_official = ?)", pluginID, userID, true).First(&plugin).Error; err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "плагин не найден"})
		return
	}

	plugin.IsEnabled = !plugin.IsEnabled
	db.Save(&plugin)
	c.JSON(http.StatusOK, plugin)
}

// горячие клавиши
func getHotkeys(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var hotkeys []Hotkey
	db.Where("user_id = ?", userID).Find(&hotkeys)
	c.JSON(http.StatusOK, gin.H{"hotkeys": hotkeys})
}

func updateHotkeys(c *gin.Context) {
	userID := c.MustGet("user_id").(uint)

	var hotkeys []Hotkey
	if err := c.ShouldBindJSON(&hotkeys); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	// удаляем старые hotkeys
	db.Where("user_id = ?", userID).Delete(&Hotkey{})

	// создаем новые
	for _, hotkey := range hotkeys {
		hotkey.UserID = userID
		db.Create(&hotkey)
	}

	c.JSON(http.StatusOK, gin.H{"hotkeys": hotkeys})
}

// markdown rendering
func renderMarkdown(c *gin.Context) {
	var req struct {
		Content string `json:"content"`
	}

	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "неверные данные"})
		return
	}

	// простая обработка markdown (в реальности лучше использовать библиотеку)
	rendered := processMarkdown(req.Content)
	c.JSON(http.StatusOK, gin.H{"html": rendered})
}

// утилиты
func generateBlockID() string {
	bytes := make([]byte, 16)
	rand.Read(bytes)
	return fmt.Sprintf("block_%x", bytes)
}

func atoi(s string) int {
	if i, err := strconv.Atoi(s); err == nil {
		return i
	}
	return 0
}

func processMarkdown(content string) string {
	// базовая обработка markdown
	html := content
	html = strings.ReplaceAll(html, "**", "<strong>")
	html = strings.ReplaceAll(html, "*", "<em>")
	html = strings.ReplaceAll(html, "# ", "<h1>")
	html = strings.ReplaceAll(html, "## ", "<h2>")
	html = strings.ReplaceAll(html, "### ", "<h3>")
	html = strings.ReplaceAll(html, "\n", "<br>")
	return html
}

func main() {
	initDB()

	r := gin.Default()

	// middleware для UTF-8
	r.Use(func(c *gin.Context) {
		c.Header("Content-Type", "application/json; charset=utf-8")
		c.Next()
	})

	// cors для мобильного приложения
	r.Use(cors.New(cors.Config{
		AllowOrigins:     []string{"*"},
		AllowMethods:     []string{"GET", "POST", "PUT", "DELETE", "OPTIONS"},
		AllowHeaders:     []string{"*"},
		ExposeHeaders:    []string{"Content-Length"},
		AllowCredentials: true,
		MaxAge:           12 * time.Hour,
	}))

	// авторизация
	r.POST("/api/register", register)
	r.POST("/api/login", login)

	// api с авторизацией
	api := r.Group("/api").Use(authMiddleware())
	{
		// заметки
		api.GET("/notes", getNotes)
		api.POST("/notes", createNote)
		api.PUT("/notes/:id", updateNote)
		api.DELETE("/notes/:id", deleteNote)
		api.GET("/notes/search", searchNotes)
		api.GET("/notes/export", exportNotes)

		// категории
		api.GET("/categories", getCategories)
		api.POST("/categories", createCategory)
		api.PUT("/categories/:id", updateCategory)
		api.DELETE("/categories/:id", deleteCategory)

		// теги
		api.GET("/tags", getTags)
		api.POST("/tags", createTag)
		api.DELETE("/tags/:id", deleteTag)

		// статистика
		api.GET("/stats", getStats)

		// шаблоны
		api.GET("/templates", getTemplates)
		api.POST("/templates", createTemplate)
		api.DELETE("/templates/:id", deleteTemplate)

		// напоминания
		api.GET("/reminders", getReminders)
		api.POST("/reminders", createReminder)
		api.PUT("/reminders/:id", updateReminder)
		api.DELETE("/reminders/:id", deleteReminder)

		// файлы
		api.POST("/files/upload", uploadFile)
		api.GET("/files", getFiles)
		api.DELETE("/files/:id", deleteFile)

		// история заметок
		api.GET("/notes/:id/history", getNoteHistory)

		// совместная работа
		api.POST("/notes/:id/share", shareNote)
		api.GET("/shares/:token", getSharedNote)
		api.GET("/shares", getMyShares)

		// backup и restore
		api.POST("/backups", createBackup)
		api.GET("/backups", getBackups)
		api.GET("/backups/:id/download", downloadBackup)

		// синхронизация
		api.POST("/sync", syncData)

		// продвинутый поиск
		api.POST("/search", advancedSearch)

		// graph view - граф связей
		api.GET("/graph", getGraph)
		api.POST("/graph/save-state", saveGraphState)
		api.GET("/graph/states", getGraphStates)
		api.GET("/graph/states/:id", getGraphState)
		api.DELETE("/graph/states/:id", deleteGraphState)

		// связи между заметками
		api.POST("/links", createLink)
		api.GET("/backlinks/:id", getBacklinks)

		// canvas - визуальные заметки
		api.GET("/canvases", getCanvases)
		api.POST("/canvases", createCanvas)
		api.PUT("/canvases/:id", updateCanvas)
		api.GET("/canvases/:id", getCanvas)
		api.DELETE("/canvases/:id", deleteCanvas)
		api.POST("/canvases/:id/save-state", saveCanvasState)
		api.POST("/canvases/:id/autosave", autoSaveCanvasState)
		api.POST("/canvases/:id/nodes", createCanvasNode)
		api.PUT("/canvases/:id/nodes/:nodeId", updateCanvasNode)
		api.DELETE("/canvases/:id/nodes/:nodeId", deleteCanvasNode)

		// ежедневные заметки
		api.GET("/daily-note", getDailyNote)

		// блоки заметок
		api.GET("/notes/:id/blocks", getNoteBlocks)
		api.PUT("/notes/:id/blocks", updateNoteBlocks)

		// рабочие пространства
		api.GET("/workspaces", getWorkspaces)
		api.POST("/workspaces", createWorkspace)
		api.PUT("/workspaces/:id/activate", activateWorkspace)

		// плагины
		api.GET("/plugins", getPlugins)
		api.PUT("/plugins/:id/toggle", togglePlugin)

		// горячие клавиши
		api.GET("/hotkeys", getHotkeys)
		api.PUT("/hotkeys", updateHotkeys)

		// markdown rendering
		api.POST("/render-markdown", renderMarkdown)
	}

	// статика для веб-клиента
	r.Static("/static", "./web/static")
	r.StaticFile("/", "./web/index.html")

	port := os.Getenv("PORT")
	if port == "" {
		port = "3000"
	}

	r.Run(":" + port)
}
