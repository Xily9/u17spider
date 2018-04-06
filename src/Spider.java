import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 爬虫类 <br/>
 * Copyright &copy; 2018 Xily.All Rights Reserved.
 * @author Xily
 */

public class Spider {

    private String baseUrl = "http://www.u17.com/comic/";
    private int order = 1;//抓取顺序,默认为按更新时间排序
    private int threads = 5;//抓取章节图片进程数,即同时抓取的章节数,默认为5个进程

    /**
     * 一切从这里开始
     */
    public void start() {
        JSONObject progress = getSpiderProgress();
        int page;
        if (progress == null) {
            page = 1;
            getComicList(page);
        } else {
            JSONArray jsonArray = progress.getJSONArray("comics");
            if (jsonArray == null || jsonArray.length()==0) {
                getComicList(progress.getInt("page") + 1);
            } else {
                getChapterList(Integer.valueOf(jsonArray.getString(0)));
            }
        }
    }

    /**
     * 当前漫画爬完,获取下一个漫画
     */
    private void getNext() {
        JSONObject progress = getSpiderProgress();
        JSONArray jsonArray = progress.getJSONArray("comics");
        int comicId = Integer.valueOf((String) jsonArray.get(0));
        jsonArray.remove(0);
        progress.put("comics", jsonArray);
        putSpiderProgress(progress);
        System.out.println("漫画" + comicId + "已经抓取完!");
        if (jsonArray.length()==0) {
            getComicList(progress.getInt("page") + 1);
        } else {
            getChapterList(Integer.valueOf((String) jsonArray.get(0)));
        }
    }

    /**
     * 获取待爬取漫画列表
     * @return 待爬取漫画列表的JSON对象
     */
    private JSONObject getSpiderProgress() {
        String string = FileUtil.get("progress.json");
        if (string != null && !string.isEmpty())
            return new JSONObject(string);
        else
            return null;
    }

    /**
     * 保存待爬取漫画列表
     * @param jsonObject 待爬取漫画列表的JSON对象
     */
    private void putSpiderProgress(JSONObject jsonObject) {
        String string = jsonObject.toString();
        FileUtil.put("progress.json", string);
    }

    /**
     * 获取漫画列表
     * @param page 页数
     */
    private void getComicList(int page) {
        System.out.println("正在抓取第" + page + "页漫画列表");
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        FormBody formBody = new FormBody.Builder()
                .add("data[order]", String.valueOf(order))
                .add("data[page_num]", String.valueOf(page))
                .add("data[group_id]", "no")
                .add("data[theme_id]", "no")
                .add("data[is_vip]", "no")
                .add("data[accredit]", "no")
                .add("data[color]", "no")
                .add("data[comic_type]", "no")
                .add("data[series_status]", "no")
                .add("data[read_mode]", "no")
                .build();
        Request request = new Request.Builder().url(baseUrl+"ajax.php?mod=comic_list&act=comic_list_new_fun&a=get_comic_list").post(formBody).build();
        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onResponse(Call arg0, Response arg1) throws IOException {
                String body = arg1.body().string();
                parseComicListJSON(body, page);
            }

            @Override
            public void onFailure(Call arg0, IOException arg1) {
                System.out.println("获取列表出错啦!请重新运行本程序!");
            }
        });

    }

    /**
     * 解析获取到的漫画列表的JSON文本
     * @param str JSON文本
     * @param page 当前页数
     */
    private void parseComicListJSON(String str, int page) {
        try {
            JSONObject jsonObject = new JSONObject(str);
            JSONArray jsonArray =jsonObject.getJSONArray("comic_list");
            if (jsonArray == null || jsonArray.length()==0) {
                System.out.println("全部漫画抓取完成!");
                System.exit(0);
            } else {
                JSONArray jsonArray2 = new JSONArray();
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject2 = jsonArray.getJSONObject(i);
                    String comicId = jsonObject2.getString("comic_id");
                    jsonArray2.put(comicId);
                    jsonObject2.put("chapters", new JSONArray());
                    //将JSON中更新时间自带的HTML去掉
                    String line2=(String)jsonObject2.get("line2");
                    Pattern pattern = Pattern.compile("title=\"(.*?)\"");
                    Matcher matcher = pattern.matcher(line2);
                    if (matcher.find()) {
                        line2=matcher.group(1);
                    }
                    jsonObject2.put("line2", line2);
                    FileUtil.put("comic_" + comicId + ".txt", jsonObject2.toString());
                }
                JSONObject jsonObject2 = new JSONObject();
                jsonObject2.put("comics", jsonArray2);
                jsonObject2.put("page", page);
                putSpiderProgress(jsonObject2);
                FileUtil.put("list_"+order+"_"+page+".json",jsonObject.toString());
                System.out.println("抓取第" + page + "页漫画列表完成!");
                start();//重新开始任务
            }
        } catch (Exception e) {
            System.out.println("解析列表出错啦!请重新运行本程序!");
        }
    }

    /**
     * 获取章节列表与简介
     * @param comicId 漫画ID
     */
    private void getChapterList(int comicId) {
        String str = FileUtil.get("comic_" + comicId + ".txt");
        if (str == null || str.isEmpty()) {
            System.out.println("漫画文本打开错误!请重新运行本程序!");
        } else {
            JSONObject jsonObject = new JSONObject(str);
            if (jsonObject.length() == 0) {
                System.out.println("漫画文本解析错误!请重新运行本程序!");
            } else {
                JSONArray chapters = jsonObject.getJSONArray("chapters");
                if (chapters == null || chapters.length()==0) {
                    System.out.println("正在抓取漫画" + comicId + "的章节列表和简介");
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(20, TimeUnit.SECONDS)
                            .build();
                    Request request = new Request.Builder().url(baseUrl + comicId + ".html").build();
                    client.newCall(request).enqueue(new Callback() {

                        @Override
                        public void onResponse(Call arg0, Response arg1) throws IOException {
                            parseChapterListHtml(comicId, arg1.body().string(), jsonObject);
                        }

                        @Override
                        public void onFailure(Call arg0, IOException arg1) {
                            System.out.println("获取章节列表出错啦!请重新运行本程序!");
                        }
                    });
                } else {
                    try {
                        parseChapterListJSON(comicId, jsonObject);
                    } catch (Exception e) {
                        System.out.println("解析章节列表出错啦!请重新运行本程序!");
                    }
                }
            }
        }
    }

    /**
     * 解析章节列表HTML
     * @param comicId 漫画ID
     * @param str HTML文本
     * @param jsonObject 保存的JSON对象
     */
    private void parseChapterListHtml(int comicId, String str, JSONObject jsonObject) {
        JSONArray jsonArray = new JSONArray();
        Document document = Jsoup.parse(str);
        Element element = document.getElementById("chapter");
        Elements elements = element.getElementsByTag("a");
        for (Element element2 : elements) {
            String href = element2.attr("href");
            String cls = element2.attr("class");
            //不是付费章节
            if (cls == null || (!cls.equals("vip_chapter") && !cls.equals("pay_chapter"))) {
                Pattern pattern = Pattern.compile("/chapter/(.*?).html");
                Matcher matcher = pattern.matcher(href);
                if (matcher.find()) {
                    String chapterId = matcher.group(1);
                    String title;
                    Pattern pattern2 = Pattern.compile("^(.*?) \\d{4}-\\d{2}-\\d{2}");
                    Matcher matcher2 = pattern2.matcher(element2.attr("title"));
                    if(matcher2.find()) {
                        title=matcher2.group(1);
                    }else {
                        title=element2.text();
                    }
                    JSONObject jsonObject2 = new JSONObject();
                    jsonObject2.put("title", title);
                    jsonObject2.put("chapterId", chapterId);
                    jsonObject2.put("images", new JSONArray());
                    jsonArray.put(jsonObject2);
                }
            }
        }
        //获取简介
        Element element2=document.getElementById("words_all");
        Elements elements2=element2.getElementsByClass("ti2");
        if(!elements2.isEmpty()) {
            String info=elements2.get(0).text();
            jsonObject.put("info", info);
        }
        jsonObject.put("chapters", jsonArray);
        FileUtil.put("comic_" + comicId + ".txt", jsonObject.toString());
        System.out.println("抓取漫画" + comicId + "的章节列表和简介完成!");
        parseChapterListJSON(comicId, jsonObject);
    }
    /**
     * 解析保存的章节列表JSON
     * @param comicId 漫画ID
     * @param jsonObject 保存的JSON对象
     */
    private void parseChapterListJSON(int comicId, JSONObject jsonObject) {
        try {
            AtomicInteger count = new AtomicInteger(0);//线程安全的计数器
            JSONArray jsonArray = (JSONArray) jsonObject.get("chapters");
            for (int i = 0; i < jsonArray.length(); i++) {
                //该章节是否抓取过
                if (jsonArray.getJSONObject(i).getJSONArray("images").length()==0) {
                    System.out.println("正在抓取漫画" + comicId + "的章节" + (i+1));
                    final int finalI = i;
                    getImages(Integer.valueOf(jsonArray.getJSONObject(i).getString("chapterId")),
                            (jsonArray2, isFinished) -> {
                                jsonArray.getJSONObject(finalI).put("images", jsonArray2);
                                jsonObject.put("chapters", jsonArray);
                                FileUtil.put("comic_" + comicId + ".txt", jsonObject.toString());
                                System.out.println("抓取漫画" + comicId + "的章节" + (finalI+1) + (isFinished ? "完成!" : "失败!"));
                                int num = count.incrementAndGet();//计数器+1
                                //判断是否抓取完
                                if (num >= jsonArray.length()) {
                                    getNext();
                                }
                            });
                } else {
                    System.out.println("漫画" + comicId + "的章节" + (i+1) + "已经抓取过,跳过");
                    int num = count.incrementAndGet();//计数器+1
                    //判断是否抓取完
                    if (num >= jsonArray.length()) {
                        getNext();
                        return;
                    }
                }
                //当设定的进程数还没工作完时等待工作完
                if (i % 5 == threads-1) {
                    while (count.get() < i + 1) {
                        Thread.sleep(500);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取章节图片列表
     * @param chapterId 章节ID
     * @param callback 回调函数
     */
    private void getImages(int chapterId, Action callback) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        Request request = new Request.Builder()
                .url(baseUrl + "ajax.php?mod=chapter&act=get_chapter_v5&chapter_id=" + chapterId).build();
        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onResponse(Call arg0, Response arg1) throws IOException {
                // parseImagesHtml(chapterId, arg1.body().string(), callback);
                parseImagesJson(arg1.body().string(), callback);
            }

            @Override
            public void onFailure(Call arg0, IOException arg1) {
                System.out.println("获取图片列表出错啦!请重新运行本程序!");
            }
        });
    }
    /**
     * 解析图片列表的JSON文本
     * @param str JSON文本
     * @param callback 回调函数
     */
    private void parseImagesJson(String str, Action callback) {
        JSONObject jsonObject=new JSONObject(str);
        JSONArray jsonArray = jsonObject.getJSONArray("image_list");
        JSONArray jsonArray2 = new JSONArray();
        if (jsonArray != null && jsonArray.length()>0) {
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject2 = jsonArray.getJSONObject(i);
                String type = jsonObject2.getString("type");
                if (type != null && !type.isEmpty()) {
                    String src =jsonObject2.getString("src");
                    jsonArray2.put(src);
                }
            }
            callback.method(jsonArray2, true);
        } else {
            jsonArray2.put("error");
            callback.method(jsonArray2, false);
        }
    }
    public interface Action{
        void method(JSONArray jsonArray,boolean isFinished);
    }


    /*
    private void parseImagesHtml(String str, MyInterface callback) {
        Pattern pattern = Pattern.compile("image_list: \\{(.*?)\\}\\},");
        Matcher matcher = pattern.matcher(str);
        if (matcher.find()) {
            try {
                JSONArray jsonArray = new JSONArray();
                JSONObject jsonObject = new JSONObject("{" + matcher.group(1) + "}}");
                for (int i = 1; i <= jsonObject.length(); i++) {
                    JSONObject jsonObject2 = jsonObject.getJSONObject(String.valueOf(i));
                    String type = jsonObject2.getString("type");
                    if (type != null && !type.isEmpty()) {
                        String src = jsonObject2.getString("src");
                        String srcDecoded = StringUtil.base64Decode(src);
                        jsonArray.put(srcDecoded);
                    }
                }
                callback.method(jsonArray, true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            JSONArray jsonArray = new JSONArray();
            jsonArray.put("error");
            callback.method(jsonArray, false);
        }
    }
    */
}