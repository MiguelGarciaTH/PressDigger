# Scribe News Image Processor

A Spring Boot application that processes images from crawled news articles.

## Overview

This module listens to Kafka messages containing article data and performs advanced image analysis on the images within those articles. It detects blank pages, performs OCR text detection, and applies auto-cropping to improve image quality.

## Features

- Processes images from news articles
- Blank page detection
- OCR text detection and extraction
- Auto-cropping using Thumbnailator library
- Publishes processed image data to Kafka

## Technologies

- Spring Boot
- Spring Kafka
- Thumbnailator (image processing)
- OCR libraries
- PostgreSQL (via scribe-news-lib)

## Configuration

Configure the application using `application.properties` or environment variables:

- Database connection settings (inherited from parent)
- Kafka broker settings
- Image processing parameters
- OCR settings

## Running

```bash
mvn spring-boot:run
```

Or build and run the JAR:

```bash
mvn clean package
java -jar target/scribe-news-image-processor-*.jar
```
