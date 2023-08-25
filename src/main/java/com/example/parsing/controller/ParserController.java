package com.example.parsing.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class ParserController {
    @GetMapping("/")
    public String parse(Model model) {
        model.addAttribute("title", "Веб-приложение парсинга YouTube каналов");
        return "parse";
    }

    @GetMapping("/parse")
    public String parseChannels() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults=10000&q=русскоязычные%20каналы&type=channel&regionCode=RU&relevanceLanguage=ru&key=AIzaSyBVtOjrEYgjVKcHmGzrg7x8OiwRtV-EQ_8"))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray items = json.getAsJsonArray("items");

            String currentDirectory = System.getProperty("user.dir");
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

            int currentRowNoEmail = sheetNoEmail.getLastRowNum() + 1;
            int currentRowWithEmail = sheetWithEmail.getLastRowNum() + 1;

            for (JsonElement item : items) {
                JsonObject channel = item.getAsJsonObject();
                JsonObject snippet = channel.getAsJsonObject("snippet");
                JsonElement channelIdElement = snippet.get("channelId");
                String channelId = channelIdElement != null ? channelIdElement.getAsString() : null;

                if (channelId != null) {
                    HttpRequest channelRequest = HttpRequest.newBuilder()
                            .uri(URI.create("https://www.googleapis.com/youtube/v3/channels?part=snippet%2Cstatistics&id=" + channelId + "&key=AIzaSyBVtOjrEYgjVKcHmGzrg7x8OiwRtV-EQ_8"))
                            .build();

                    HttpResponse<String> channelResponse = client.send(channelRequest, HttpResponse.BodyHandlers.ofString());
                    String channelResponseBody = channelResponse.body();

                    JsonObject channelJson = JsonParser.parseString(channelResponseBody).getAsJsonObject();
                    JsonArray channelItems = channelJson.getAsJsonArray("items");

                    if (channelItems != null && channelItems.size() > 0) {
                        JsonObject channelItem = channelItems.get(0).getAsJsonObject();
                        JsonObject channelStatistics = channelItem.getAsJsonObject("statistics");
//                       JsonObject channelSnippet = channelItem.getAsJsonObject("snippet");
//                       JsonElement descriptionElement = channelSnippet.get("description");

                        String subscriberCount = channelStatistics.get("subscriberCount").getAsString();
                        String email = "";
                        // Использование Jsoup для веб-скрапинга страницы канала
                        Document document = Jsoup.connect("https://www.youtube.com/channel/" + channelId + "/about").get();
                        Element descriptionElement = document.select("div#description").first();
                        if (descriptionElement != null) {
                            String description = descriptionElement.text();
                            Pattern pattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}");
                            Matcher matcher = pattern.matcher(description);
                            if (matcher.find()) {
                                email = matcher.group();
                            }
                        }
//                        if (descriptionElement != null) {
//                            String description = descriptionElement.getAsString();
//
//                            Pattern pattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}");
//                            Matcher matcher = pattern.matcher(description);
//                            if (matcher.find()) {
//                                email = matcher.group();
//                            }
//                        }
                        Row newRowNoEmail = sheetNoEmail.createRow(currentRowNoEmail);
                        newRowNoEmail.createCell(0).setCellValue("https://www.youtube.com/channel/" + channelId);
                        newRowNoEmail.createCell(1).setCellValue(subscriberCount);
                        currentRowNoEmail++;

                        if (!email.isEmpty() ) {
                            Row newRowWithEmail = sheetWithEmail.createRow(currentRowWithEmail);
                            newRowWithEmail.createCell(0).setCellValue("https://www.youtube.com/channel/" + channelId);
                            newRowWithEmail.createCell(1).setCellValue(email);
                            newRowWithEmail.createCell(2).setCellValue(subscriberCount);
                            currentRowWithEmail++;
                        }

                    } else {
                        System.out.println("Ошибка при получении информации о канале: " + channelId);
                    }
                } else {
                    System.out.println("Ошибка при получении channelId канала");
                }
            }
                FileOutputStream fileOutputStreamNoEmail = new FileOutputStream(fileNoEmail);
                workbookNoEmail.write(fileOutputStreamNoEmail);
                fileOutputStreamNoEmail.close();

                FileOutputStream fileOutputStreamWithEmail = new FileOutputStream(fileWithEmail);
                workbookWithEmail.write(fileOutputStreamWithEmail);
                fileOutputStreamWithEmail.close();

                return "parse";
            } catch(IOException | InterruptedException e ){
                e.printStackTrace();
                return "error";
            }
        }
    }
