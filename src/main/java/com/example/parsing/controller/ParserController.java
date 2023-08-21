package com.example.parsing.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
@Controller
public class ParserController {
    @GetMapping("/")
    public String parse(Model model) {
        model.addAttribute("title", "Веб-приложение парсинга YouTube каналов");
        return "parse"; // Возвращает имя HTML-файла без расширения
    }
    @GetMapping("/parse")
    public String parseChannels() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=10&q=русскоязычные%20каналы&type=channel&regionCode=RU&relevanceLanguage=ru&key=AIzaSyBVtOjrEYgjVKcHmGzrg7x8OiwRtV-EQ_8"))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray items = json.getAsJsonArray("items");

            String currentDirectory = System.getProperty("user.dir"); // Получить текущую рабочую директорию проекта
            String filePath = currentDirectory + "/src/main/java/com/example/parsing/result/";

            File fileNoEmail = new File(filePath + "noemail.xls");
            File fileWithEmail = new File(filePath + "email.xls");

            Workbook workbookNoEmail;
            Workbook workbookWithEmail;

            if (fileNoEmail.exists()) {
                workbookNoEmail = WorkbookFactory.create(fileNoEmail);
            } else {
                workbookNoEmail = new HSSFWorkbook();
                Sheet sheet = workbookNoEmail.createSheet("Sheet1");

                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("Channel URL");
                headerRow.createCell(1).setCellValue("Subscriber Count");
            }

            if (fileWithEmail.exists()) {
                workbookWithEmail = WorkbookFactory.create(fileWithEmail);
            } else {
                workbookWithEmail = new HSSFWorkbook();
                Sheet sheet = workbookWithEmail.createSheet("Sheet1");

                Row headerRow = sheet.createRow(0);
                headerRow.createCell(0).setCellValue("Channel URL");
                headerRow.createCell(1).setCellValue("Email");
                headerRow.createCell(2).setCellValue("Subscriber Count");
            }

            Sheet sheetNoEmail = workbookNoEmail.getSheet("Sheet1");
            Sheet sheetWithEmail = workbookWithEmail.getSheet("Sheet1");

            // Переменные для отслеживания текущей строки в файле
            int currentRowNoEmail = sheetNoEmail.getLastRowNum() + 1;
            int currentRowWithEmail = sheetWithEmail.getLastRowNum() + 1;

            for (JsonElement item : items) {
                JsonObject channel = item.getAsJsonObject();
                JsonObject snippet = channel.getAsJsonObject("snippet");
                JsonElement channelIdElement = snippet.get("channelId");
                String channelId = channelIdElement != null ? channelIdElement.getAsString() : null;

                // Запрос на получение информации о канале
                if (channelId != null) {
                    HttpRequest channelRequest = HttpRequest.newBuilder()
                            .uri(URI.create("https://www.googleapis.com/youtube/v3/channels?part=snippet%2Cstatistics%2Cemail&id=" + channelId + "&key=AIzaSyBVtOjrEYgjVKcHmGzrg7x8OiwRtV-EQ_8"))
                            .build();

                    HttpResponse<String> channelResponse = client.send(channelRequest, HttpResponse.BodyHandlers.ofString());
                    String channelResponseBody = channelResponse.body();

                    JsonObject channelJson = JsonParser.parseString(channelResponseBody).getAsJsonObject();
                    JsonArray channelItems = channelJson.getAsJsonArray("items");

                    // Обработка информации о канале и сохранение данных в файл
                    if (channelItems != null && channelItems.size() > 0) {
                        JsonObject channelItem = channelItems.get(0).getAsJsonObject();
                        JsonObject channelStatistics = channelItem.getAsJsonObject("statistics");
                        JsonObject channelSnippet = channelItem.getAsJsonObject("snippet");
                        JsonElement emailElement = channelSnippet.get("email");

                        // Получение необходимых данных о канале
                        String subscriberCount = channelStatistics.get("subscriberCount").getAsString();
                        String email = "";
                        if (emailElement != null) {
                            email = emailElement.getAsString();
                        }

                        // Создание новых строк и заполнение данными
                        Row newRowNoEmail = sheetNoEmail.createRow(currentRowNoEmail);
                        newRowNoEmail.createCell(0).setCellValue("https://www.youtube.com/channel/" + channelId);
                        newRowNoEmail.createCell(1).setCellValue(subscriberCount);

                        Row newRowWithEmail = sheetWithEmail.createRow(currentRowWithEmail);
                        newRowWithEmail.createCell(0).setCellValue("https://www.youtube.com/channel/" + channelId);
                        newRowWithEmail.createCell(1).setCellValue(email);
                        newRowWithEmail.createCell(2).setCellValue(subscriberCount);

                        // Увеличение индекса текущей строки
                        currentRowNoEmail++;
                        currentRowWithEmail++;
                    } else {
                        System.out.println("Ошибка при получении информации о канале: " + channelId);
                    }
                } else {
                    System.out.println("Ошибка при получении channelId канала");
                }
            }

            // Сохранение данных в файлы
            FileOutputStream fileOutputStreamNoEmail = new FileOutputStream(fileNoEmail);
            workbookNoEmail.write(fileOutputStreamNoEmail);
            fileOutputStreamNoEmail.close();

            FileOutputStream fileOutputStreamWithEmail = new FileOutputStream(fileWithEmail);
            workbookWithEmail.write(fileOutputStreamWithEmail);
            fileOutputStreamWithEmail.close();

            return "parse";
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "error";
        }
    }
}
