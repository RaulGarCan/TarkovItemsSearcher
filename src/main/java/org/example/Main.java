package org.example;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class Main {
    public static LocalDate lastAppPatch = LocalDate.of(2026, Month.JANUARY, 19);
    public static String dataFolderPath = System.getProperty("user.home") + File.separator +"Documents"+"/AppTarkov";
    public static int numberOfFiles = 18;
    static class Items {
        Item[] items;
        Items(){}
        Items(Item[] items){
            this.items = items;
        }

        public Item[] getItems() {
            return items;
        }

        public void setItems(Item[] items) {
            this.items = items;
        }

        @Override
        public String toString() {
            return "Data{" +
                    "items=" + Arrays.toString(items) +
                    '}';
        }
    }
    static class Item {
        @SerializedName("name")
        String name;
        @SerializedName("shortName")
        String shortName;
        Item(){}
        Item(String name, String shortName){
            this.name = name;
            this.shortName = shortName;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getShortName() {
            return shortName;
        }

        public void setShortName(String shortName) {
            this.shortName = shortName;
        }

        @Override
        public String toString() {
            return "Item{" +
                    "name='" + name + '\'' +
                    ", shortName='" + shortName + '\'' +
                    '}';
        }
    }
    public static void CheckLastAPICall(File dataFolder){
        FileTime fileTime = GetLastModified(dataFolder);
        System.out.println("\nLast Updated Data: "+fileTime.toString().substring(0,10));
        System.out.println("Enter 1 to update the data");
    }
    public static FileTime GetLastModified(File dataFolder){
        try {
            return Files.getLastModifiedTime(Paths.get(dataFolder.listFiles()[0].getAbsolutePath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void ClearConsole(){
        try {
            new ProcessBuilder("cmd","/c","cls").inheritIO().start().waitFor();
        } catch (InterruptedException | IOException e) {
            System.out.println(e);
        }
    }
    public static void main(String[] args) {
        ClearConsole();

        //System.out.print("Introduce el item a buscar: ");
        //Scanner sc = new Scanner(System.in);
        //API(sc.nextLine());

        File dataFolder = new File(dataFolderPath);
        LocalDate lastUpdate = LocalDate.ofInstant(GetLastModified(dataFolder).toInstant(), ZoneId.systemDefault());
        if(lastUpdate.isBefore(lastAppPatch)){
            System.out.println("Auto update required!");
            API();
        }
        if(!dataFolder.exists() || dataFolder.listFiles()==null){
            System.out.println("Data folder not found!");
            API();
        }
        CheckLastAPICall(dataFolder);
        boolean exec = true;

        //ClearConsole();

        do {
            ArrayList<Item> items = new ArrayList<>();
            ArrayList<ItemFinderThread> activeThreads = new ArrayList<>();
            Scanner sc = new Scanner(System.in);
            System.out.print("\nIntroduce part of the name of the item: ");
            String itemName = sc.nextLine();
            if(itemName.equalsIgnoreCase("1")){
                API();
            }
            else {
                for(int i=0; i<numberOfFiles; i++){
                    ItemFinderThread thread = new ItemFinderThread(i+1, itemName);
                    thread.start();
                    activeThreads.add(thread);
                }
                for(int i=0; i<activeThreads.size(); i++){
                    if(activeThreads.get(i).isAlive() || (activeThreads.get(i).getItemList()!=null && activeThreads.get(i).getItemList().isEmpty())){
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        if(activeThreads.get(i).getItemList()!=null){
                            items.addAll(activeThreads.get(i).getItemList());
                        }
                    }
                }

                if(!items.isEmpty()){
                    if(items.size()<10){
                        Item selectedItem = items.get(0);
                        if(items.size()>1){
                            selectedItem = ItemMenuSelection(items);
                        }
                        System.out.println("\nSelected Item: "+selectedItem.name);
                        String url = "https://escapefromtarkov.fandom.com/wiki/"+selectedItem.name.replaceAll(" ","_");

                        String wikiPage = GetWikiPage(url);

                        wikiPage = wikiPage.replaceAll("Quests\\[]", "Quests");
                        wikiPage = wikiPage.replaceAll("Hideout\\[]", "Hideout");

                        if(!wikiPage.trim().isBlank()){
                            System.out.println("\n"+wikiPage);
                        } else {
                            System.out.println("\nThis item is not required for Hideout or Quests\n");
                        }
                        System.out.println("Source: "+url);
                    } else {
                        System.out.println("Too many options, be more specific");
                        //System.out.println(items.size());
                    }

                    System.out.println("\n");
                }
            }
        }while (exec);
    }
    public static String GetWikiPage(String url){
        URL uri;
        try {
            uri = new URL(url);
            HttpURLConnection con;
            try {
                con = (HttpURLConnection) uri.openConnection();
                con.setRequestMethod("GET");
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer content = new StringBuffer();
                boolean saveLine = false;
                boolean endOfQuests = false;
                while ((inputLine = in.readLine()) != null) {
                    if(inputLine.contains("span class=\"mw-headline\" id=\"")){
                        saveLine = false;
                    }
                    if(inputLine.contains("id=\"Quests\"") || inputLine.contains("id=\"Hideout\"")){
                        saveLine = true;
                        if(inputLine.contains("id=\"Hideout\"")){
                            endOfQuests = true;
                        }
                    }
                    if(saveLine){
                        inputLine = inputLine.replaceAll("<[^>]+>", "");
                        if(!inputLine.toLowerCase().contains("quests[]") && !inputLine.toLowerCase().contains("hideout[]") && !inputLine.toLowerCase().contains("story chapter") && !endOfQuests){
                            String questName = GetQuestName(inputLine);
                            String wikiURL = "https://escapefromtarkov.fandom.com/wiki/";
                            //System.out.println(wikiURL);
                            wikiURL += URLEncoder.encode(questName.replaceAll(" ","_"), "UTF-8");
                            String additionalInfo = GetWikiQuestPage(wikiURL, questName.replaceAll(" ","_"));
                            //System.out.println(additionalInfo);
                            inputLine += " "+additionalInfo;
                        }
                        content.append(inputLine+"\n");
                    }
                }
                in.close();
                con.disconnect();

                return content.toString();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
    public static String GetWikiQuestPage(String url, String questName){
        URL uri;
        String result = "ERROR";
        try {
            uri = new URL(url);
            HttpURLConnection con;
            try {

                con = (HttpURLConnection) uri.openConnection();
                con.setRequestMethod("GET");
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                String inputLine;
                boolean saveLine = false;

                BufferedWriter writer = new BufferedWriter(new FileWriter("C:\\Users\\BYR\\Desktop\\"+questName.replaceAll("\\?","")+".xml"));
                while ((inputLine = in.readLine()) != null) {
                    writer.write(inputLine+"\n");
                    if(saveLine){
                        String isKappaRequired = "No";
                        if(inputLine.contains(">Yes<")){
                            isKappaRequired = "Yes";
                        }
                        String trader = GetTraderName(inputLine);
                        result = "["+trader+": Kappa -> "+isKappaRequired+"]";
                        saveLine = false;
                    }
                    if(inputLine.contains("<!-- End Google Tag Manager (noscript) -->")){
                        saveLine = true;
                        //System.out.println(questName+": Save Line");
                    }
                }
                writer.close();
                in.close();
                con.disconnect();

                return result;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
    public static String GetTraderName(String line){
        String[] traderNames = {
            "Prapor", "Therapist", "Fence", "Skier", "Peacekeeper", "Mechanic", "Ragman", "Jaeger", "Ref", "Lightkeeper", "BTR Driver"
        };
        for (String name : traderNames){
            if(line.toLowerCase().contains(name.toLowerCase())){
                return name;
            }
        }
        return "Not found";
    }
    public static Item ItemMenuSelection(ArrayList<Item> items){
        System.out.println();
        for(int i=0; i<items.size(); i++){
            System.out.println(i+1+") "+items.get(i).name);
        }
        System.out.println();
        do {
            Scanner sc = new Scanner(System.in);
            try{
                System.out.print("Select an option: ");
                int selection = Integer.parseInt(sc.nextLine());
                if(selection>=0 && selection<=items.size()){
                    return items.get(selection-1);
                } else {
                    System.out.println("ERROR: Introduce a number within the range");
                }
            }catch (NumberFormatException e){
                System.out.println("ERROR: Introduce a valid number");
            }
        }while (true);
    }
    public static String GetQuestName(String line){
        String tmp = line;
        tmp = tmp.replaceAll(".*for the quest","").trim();
        tmp = tmp.replaceAll("\\(.*","").trim();
        return tmp;
    }
    public static void API(){ // Only call to update cached information
        /*
        String query = String.format("""
        {
          "query": "query ($name: String!) { items(lang: en, name: $name) { id name shortName } }",
          "variables": {
            "name": "%s"
          }
        }
        """, itemName);
         */
        System.out.println("Retrieving information, please wait...");

        String query = "{\"query\": \"{ items {name shortName} }\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.tarkov.dev/graphql"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(query))
                .build();

        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        //System.out.println("Status: " + response.statusCode());
        //System.out.println("Body:");
        String s = response.body().substring(8,response.body().length()-1);
        //System.out.println(s);

        Gson gson = new Gson();
        Items d = gson.fromJson(s, Items.class);
        Item[] items = d.getItems();
        //System.out.println(items.length);

        WriteDataJson(items, numberOfFiles, gson);
    }
    public static void WriteDataJson(Item[] items, int numberOfFiles, Gson gson){
        int fileCounter = 1;
        File dataFolder = new File(dataFolderPath);
        //System.out.println(dataFolder.getAbsolutePath());
        if(!dataFolder.exists()){
            dataFolder.mkdir();
        }
        ArrayList<Item> itemList = new ArrayList<>();
        for(int i=0; i<items.length; i++){
            itemList.add(items[i]);
            if(itemList.size()==(items.length/numberOfFiles) || i==items.length-1){
                String json = gson.toJson(itemList);
                try {
                    FileWriter writer = new FileWriter(dataFolder+"/data"+fileCounter+".json");
                    writer.write(json);
                    writer.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                itemList = new ArrayList<>();
                fileCounter++;
            }
        }
    }
    static class ItemFinderThread extends Thread {
        ArrayList<Item> itemList;
        int threadNumber;
        String itemName;
        ItemFinderThread(int threadNumber, String itemName){
            this.threadNumber = threadNumber;
            this.itemName = itemName;
            itemList = new ArrayList<>();
        }

        public ArrayList<Item> getItemList() {
            return itemList;
        }

        @Override
        public void start() {
            ArrayList<Item> tmp = new ArrayList<>();

            Gson gson = new Gson();
            try {
                BufferedReader reader = new BufferedReader(new FileReader(dataFolderPath+"/data"+threadNumber+".json"));
                try {
                    String json = "";
                    String s;
                    while((s = reader.readLine()) != null){
                        json+=s;
                    }
                    Item[] itemArray = gson.fromJson(json, Item[].class);
                    for(Item item : itemArray){
                        if(item.getName().toLowerCase().contains(itemName.toLowerCase()) || item.getShortName().toLowerCase().contains(itemName.toLowerCase())){
                            tmp.add(item);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            if(!tmp.isEmpty()){
                itemList.addAll(tmp);
            } else {
                itemList = null;
            }
        }
    }
}