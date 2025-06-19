.PHONY: help build run-server run-android clean test

help: ## показать справку
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

build: ## сборка всех компонентов
	@echo "🔨 сборка сервера..."
	go build -o bin/redeluxe-server ./server
	@echo "✅ сервер собран"

run-server: ## запуск сервера
	@echo "🚀 запуск redeluxe сервера..."
	go run ./server/main.go

run-dev: ## запуск в режиме разработки
	@echo "🔧 запуск в dev режиме..."
	air -c .air.toml

run-android: ## сборка и установка android приложения
	@echo "📱 сборка android приложения..."
	cd android && ./gradlew assembleDebug
	@echo "📲 установка на устройство..."
	cd android && ./gradlew installDebug

clean: ## очистка файлов сборки
	@echo "🧹 очистка..."
	rm -rf bin/
	rm -f redeluxe.db
	cd android && ./gradlew clean

test: ## запуск тестов
	@echo "🧪 запуск тестов..."
	go test -v ./...

deps: ## установка зависимостей
	@echo "📦 установка go зависимостей..."
	go mod download
	go mod tidy

setup-android: ## настройка android окружения
	@echo "⚙️ настройка android..."
	cd android && ./gradlew wrapper --gradle-version 7.6

docker-build: ## сборка docker образа
	@echo "🐳 сборка docker образа..."
	docker build -t redeluxe:latest .

docker-run: ## запуск в docker
	@echo "🐳 запуск в docker..."
	docker run -p 8080:8080 redeluxe:latest

init: ## инициализация проекта
	@echo "🎯 инициализация redeluxe..."
	@echo "📁 создание директорий..."
	mkdir -p bin logs
	@echo "📦 установка зависимостей..."
	make deps
	@echo "✅ проект готов к работе!"
	@echo "💡 используйте 'make run-server' для запуска"

prod: ## запуск в продакшене
	@echo "🚀 production запуск..."
	PORT=8080 ./bin/redeluxe-server

install: build ## установка в систему
	@echo "📦 установка redeluxe..."
	sudo cp bin/redeluxe-server /usr/local/bin/
	@echo "✅ установлено в /usr/local/bin/" 