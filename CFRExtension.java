package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * JS1_PXCFR
 * @author 강태훈
 *
 */
public class CFRExtension implements Extension, AddonExtension {
    /* 테스트용 S */
    private boolean isTest;
    /* 테스트용 E */

    private final ConnectionUtil connectionUtil = new ConnectionUtil();
    private final CommonUtil commonUtil = new CommonUtil();

    private String cl_cd;
    private String origin_cd;
    private boolean error_exist = false;
    private int doc_id;
    private List<HashMap<String, String>> attaches_info;
    private String now_time;
    private String file_name;


    Map<String, String> imgHeader = new HashMap<>();

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("StartExtension!!!!!");

        Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
        doc_id = 0;
        attaches_info = new ArrayList<>();

        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);

        imgHeader.put("User-Agent", commonUtil.generateRandomUserAgent());

        /* 테스트용 S */
        isTest = connectionUtil.isTest(reposit);
        /* 테스트용 E */
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        Map<String, String> map = new HashMap<String, String>();
        map.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/103.0.0.0 Safari/537.36");
        map.put("Host", "www.cfr.org");
        map.put("Cache-Control", "no-cache");
        map.put("Accept-Language", "de,en;q=0.7,en-us;q=0.3");
        map.put("Accept", "*/*");
        map.put("Referer", "https://www.cfr.org");
        return map;
    }

       @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
           Document document = Jsoup.parse(htmlSrc);

           if(dqPageInfo.getParentUrl() == null) {  // 0depth
               String domain = commonUtil.getDomain(dqPageInfo.getUrl());
               String urlListCssQuery = "";
               if (dqPageInfo.getUrl().contains("/books-reports")) {
                   urlListCssQuery = ".landing-cards-grid__list-container .card-article__link-outer-wrapper";
               } else if (dqPageInfo.getUrl().contains("-korea")) {
                   urlListCssQuery = ".views-infinite-scroll-content-wrapper .card-article-large__link";
               } else{
                   urlListCssQuery = ".NONE";
               }
               Elements urlList = document.select(urlListCssQuery);
               htmlSrc = commonUtil.getUrlList(urlList, domain);
               System.out.println("htmlSrc = " + htmlSrc);
           } else {    //1depth
               /* 변수선언 start */
               String title = "";
               String content = "";
               String datetime = "";
               /* 변수선언 end */

               /* 파싱 start */
               //title
               title = document.select(".article-header-blog__title, .article-header__title,.hero__title").text();
               if(title.equals("")){
                   if (dqPageInfo.getUrl().contains("task-force-report")) {
                       title = document.select(".hero__title").text();
                   }else if (dqPageInfo.getUrl().contains("/event/")) {
                       title = document.select(".msp-event-header-past__title").text();
                   }else if (dqPageInfo.getUrl().contains("/podcasts/")) {
                       title = document.select(".podcast-header__title").text();
                   }else if (dqPageInfo.getUrl().contains("/backgrounder/")) {
                       title = document.select(".article-header-backgrounder__title").text();
                   }else if (dqPageInfo.getUrl().contains("/timeline/")) {
                       title = document.select(".timeline-header__title").text();
                   }
               }


               //create date
               Elements jsonElements = document.select("script[type=application/ld+json]");
               if (jsonElements.toString().equals("")) {
                   String datetimeHtml = document.select(".hero__date").text();
                   // 파싱할 월 이름과 년도 추출
                   String[] parts = datetimeHtml.split(" ");
                   Month month = Month.valueOf(parts[1].toUpperCase());
                   int year = Integer.parseInt(parts[2]);

                   // LocalDateTime 생성
                   LocalDateTime localDateTime = LocalDateTime.of(year, month, 1, 0, 0);

                   // 원하는 형식으로 포맷
                   DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                   datetime = localDateTime.format(outputFormatter);
               }else{
                   String jsonData = jsonElements.first().html();
                   JSONParser parser = new JSONParser();
                   try {
                       JSONObject jsonObject = (JSONObject) parser.parse(jsonData);
                       JSONObject webPageObject = (JSONObject) ((JSONArray) jsonObject.get("@graph")).get(0);
                       String datePublished = (String) webPageObject.get("datePublished");
                       String dateTimeWithoutOffset = datePublished.substring(0, datePublished.length() - 5);
                       LocalDateTime localDateTime = LocalDateTime.parse(dateTimeWithoutOffset, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                       DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                       datetime = localDateTime.format(outputFormatter);
                   } catch (ParseException e) {
                       e.printStackTrace();
                   }
               }

               //content
               if(dqPageInfo.getUrl().contains("/timeline")){
                   Elements contentHtml = document.select(".timeline-slides");
                   contentHtml.select("source, nav, .timeline-bg__item, .timeline-slide__dates-header, figcaption, .timeline-slide__shadow").remove();
                   commonUtil.removeDuplicateHref(contentHtml);    //중복되는 a태그 한개만 남기고 제거
                   // pdf를 제외한 href 속성 제거
                   commonUtil.removeHrefExceptPDF(contentHtml);
                   content += contentHtml.toString()
                           .replace("//cdn.cfr.org/", "https://cdn.cfr.org/")
                           .replace(".webp" ,"");
               }else{
                   //메인 이미지
                   Elements leadImages = document.select(".article-header-blog__figure, .article-header__image-picture");
                   if(leadImages.select("img").size() > 0){
                       content = leadImages.select("img").removeAttr("srcset").first().toString()
                               .replace("//cdn.cfr.org/", "https://cdn.cfr.org/")
                               .replace(".webp" ,"");
                   }

                   //본문 내용
                   Elements contentHtml = document.select(".body-content,.supplemental-content2");
                   contentHtml.select(".media-episode-card, .more-on, .desktop-only, .button-container, .authors-block" +
                           ", .pullquote, .newsletter-tout, source, figcaption, script, link, iframe, svg").remove();

                   // 특이 케이스 있음 https://www.cfr.org/task-force-report/confronting-reality-in-cyberspace
                   if (contentHtml.toString().equals("")) {
                       contentHtml = document.select(".main-content__wrapper");
                       contentHtml.select("#about-the-task-force, .container-fluid, div[data-section=order-a-copy]" +
                               ", button, .chapters-nav__list, .chapters-nav__full-report-link" +
                               ", .paragraph--type--pull-quote, iframe, script, source, figcaption, .chapters-nav__header-mobile").remove();
                   }

                   Elements downloadLinks = document.select("a[data-event=downloadpdf_sticky]");

                   for (Element link : downloadLinks) {
                       link.text(""); // Remove the text content of the link
                   }

                   //viewer 제거
                   Elements links = contentHtml.select("a");
                   for (Element link : links) {
                       String href = link.attr("href");
                       if (href.contains("/viewer")) {
                           link.attr("href", "#");
                       }
                   }

                   commonUtil.removeDuplicateHref(contentHtml);    //중복되는 a태그 한개만 남기고 제거
                   // pdf를 제외한 href 속성 제거
                   commonUtil.removeHrefExceptPDF(contentHtml);
                   content += contentHtml.toString()
                           .replace("//cdn.cfr.org/", "https://cdn.cfr.org/")
                           .replace(".webp" ,"");
               }
               /* 파싱 end */

               /* 태그 생성 start */
               htmlSrc = commonUtil.makeCollectContext(title, datetime, content.replace("data-src=", "src=").replace("amp;", ""));
               /* 태그 생성 end */
           }

        return htmlSrc;
    }



    @Override
    public List<String> makeNewUrls(String naviUrl, DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public Row[] splitRow(Row row, DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public void changeRowValue(Row row, DqPageInfo dqPageInfo) {
        // doc_id, cl_cd, origin_cd 번호 넣어주는 부분
        doc_id++;
        for (int i = 0; i < row.size(); i++) {
            String nodeId = row.getNodeByIdx(i).getId();
            String nodeName = row.getNodeByIdx(i).getName();
            String nodeValue = row.getNodeByIdx(i).getValue();

            if (nodeName.equals("source_class")) {
                cl_cd = nodeValue;
            } else if (nodeName.equals("source_ID")) {
                origin_cd = nodeValue;
            }

            if (nodeName.equals("document_id")) {
                row.getNodeByIdx(i).setValue(String.format("%06d", doc_id));
            }

        }
    }

    @Override
    public boolean validData(Row row, DqPageInfo dqPageInfo) {
        boolean isCheck = true;
        String title = "";
        String content = "";
        String documentId = String.format("%06d", doc_id);

        try {
            for (int i = 0; i < row.size(); i++) {
                String nodeName = row.getNodeByIdx(i).getName();
                String nodeValue = row.getNodeByIdx(i).getValue();
                if (nodeName.equals("title")) {
                    title = nodeValue;
                } else if (nodeName.equals("content")) {
                    content = nodeValue;
                }
            }

            if (title.equals("") || content.equals("")) {
                isCheck = false;
                connectionUtil.upFailDocFileDownloadCount();    // 에러 파일수 판단용
            } else {
                connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time, imgHeader);
            }
        } catch (Exception e) {
            isCheck = false;
            connectionUtil.upFailDocFileDownloadCount();    // 에러 파일수 판단용
            e.printStackTrace();
        }

        /*boolean isCheck = true;
        String title = "";
        String documentId = String.format("%06d", doc_id);

        try {
            for (int i = 0; i < row.size(); i++) {
                String nodeName = row.getNodeByIdx(i).getName();
                String nodeValue = row.getNodeByIdx(i).getValue();
                if (nodeName.equals("title")) {
                    title = nodeValue;
                    break;
                }
            }

//            connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time);

            connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time, imgHeader);
            if (title.equals("")) {
                isCheck = false;
                connectionUtil.upFailDocFileDownloadCount();    // 에러 파일수 판단용
            }
        } catch (Exception e) {
            isCheck = false;
            connectionUtil.upFailDocFileDownloadCount();    // 에러 파일수 판단용
            e.printStackTrace();
        }*/

        return isCheck;
    }

    @Override
    public void endExtension(DqPageInfo dqPageInfo) {
        try {
            file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
            String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);
            if (!connectionUtil.isLocal() && !isTest) { /* 로컬에서 작업하거나 테스트 상태일 경우에는 로그를 남기지 않는다. */
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);  /* 수집로그 저장 */
            }
            connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name, isTest);
            System.out.println("첨부파일 목록 : " + attaches_info.toString());
            connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info, isTest);  /* 첨부파일 저장 */
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("extension 종료 문구");
        }
    }

    @Override
    public Map<String, String> addAttachFileRequestHeader(String s) {
        return imgHeader;
    }

}
