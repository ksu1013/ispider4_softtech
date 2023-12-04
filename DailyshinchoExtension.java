package extension;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;

/**
 * @title 일일신조 수집 Extension (PXDSC)
 * @version 1.1 (2023-08-09)
 * @since 2023-07-14
 * @author 전제현
 */
public class DailyshinchoExtension implements Extension, AddonExtension {

    private CommonUtil commonUtil;
    private ConnectionUtil connectionUtil;
    private int doc_id;
    private String cl_cd;
    private String origin_cd;
    private String now_time;
    private String file_name;
    private String url2;
    private StringBuffer tagList;
    private boolean isTest;
    private boolean error_exist;
    private Proxy proxy;
    private List<HashMap<String, String>> attaches_info;
    private Map<String, String> imgHeader;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("=== DailyshinchoExtension Start ===");
        Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
        commonUtil = new CommonUtil();
        connectionUtil = new ConnectionUtil();
        doc_id = 0;
        attaches_info = new ArrayList<>();
        error_exist = false;
        tagList = new StringBuffer();
        isTest = connectionUtil.isTest(reposit);

        String proxyHost = connectionUtil.getProxyIp();
        int proxyPort = connectionUtil.getProxyPortNumber();
        proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));

        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);

        imgHeader = new HashMap<>();
        imgHeader.put("User-Agent", commonUtil.generateRandomUserAgent());
        imgHeader.put("Cache-Control", "max-age=0");
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
    	if (url.contains("page")) {
    		url = url.replaceAll("\\?page=", "\\/page\\/");	
    	} else if(url.contains("/article/2023/")) {
    		url = url + "?all=1";
    	}
    	this.url2 = url;
        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
    	Map<String, String> header = new HashMap<String, String>();
    	header.put("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36");
        return header;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {

    	if (pageUrlMatchesPattern(url2)) {
    		Document doc = Jsoup.parse(htmlSrc);
            Element timeElement = doc.selectFirst("time");
            String datetime = timeElement.attr("datetime");
            String date = datetime.substring(0, 10);
            htmlSrc = "<bdate>" + date + "</bdate>\n";
            Element h1Element = doc.selectFirst("h1.c-title--single");
            String title = h1Element.text();
            htmlSrc +="<btitle>" + title + "</btitle>\n";
            Element paginationElement = doc.selectFirst("p.p-pagination");
            htmlSrc += "<bcontent>";
            String content = "";
        	if (paginationElement == null) {
        		Elements paragraphs = doc.select("div.p-block > p, .c-box-photo__image");
        		for (Element paragraph : paragraphs) {
            	    content += paragraph.outerHtml();
            	}
        		htmlSrc += content;
        	} else if (paginationElement != null) {
        		String htmlSrcTemp = "";
        	    Elements pageLinks = paginationElement.select("a.p-pagination__link");
        	    if (!pageLinks.isEmpty()) {
        	        Element lastPageLink = pageLinks.last();
        	        String lastPageUrl = lastPageLink.attr("href");
        	        String[] parts = lastPageUrl.split("page=");
        	        if (parts.length == 2) {
        	            String lastPageNumber = parts[1];
        	            int lastNum = Integer.parseInt(lastPageNumber);
        	            for (int i = 1; i <= lastNum; i++) {
        	            	String pageUrl = url2 + "&page=" + i;
        	            	htmlSrcTemp += commonUtil.getProductDetailPage(pageUrl, "UTF-8");
        	            	Document doc2 = Jsoup.parse(htmlSrcTemp);
        	            	Elements paragraphs = doc2.select("div.p-block > p, .c-box-photo__image");
        	            	for (Element paragraph : paragraphs) {
        	            	    content += paragraph.outerHtml();
        	            	}
        	            	htmlSrc += content;
        	            }
        	        }
        	    }
        	}
            htmlSrc += "</bcontent>";
    	}

        return htmlSrc;
    }

    @Override
    public List<String> makeNewUrls(String maviUrl, DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public Row[] splitRow(Row row, DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public void changeRowValue(Row row, DqPageInfo dqPageInfo) {
        doc_id++;
        for (int i = 0; i < row.size(); i++) {
            String nodeId = row.getNodeByIdx(i).getId();
            String nodeName = row.getNodeByIdx(i).getName();
            String nodeValue = row.getNodeByIdx(i).getValue();
            if (nodeName.equals("source_class")) {
                cl_cd = nodeValue;
            } else if (nodeName.equals("source_ID")) {
                origin_cd = nodeValue;
            } else if (nodeName.equals("document_id")) {
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
    	Map<String, String> header = new HashMap<String, String>();
    	header.put("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36");
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
                connectionUtil.upFailDocFileDownloadCount();	// 에러 파일수 판단용
            } else {
                connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time);
            }
        } catch (Exception e) {
            isCheck = false;
            connectionUtil.upFailDocFileDownloadCount();	// 에러 파일수 판단용
            e.printStackTrace();
        }

        return isCheck;
    }

    @Override
    public void endExtension(DqPageInfo dqPageInfo) {
        try {
            file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
            String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);
            if (!connectionUtil.isLocal() && !isTest) {
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);  /* 수집로그 저장 */
            }
            connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name, isTest);
            System.out.println("첨부파일 목록 : " + attaches_info.toString());
            connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info, isTest);  /* 첨부파일 저장 */
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("=== DailyshinchoExtension End ===");
        }
    }

	@Override
	public Map<String, String> addAttachFileRequestHeader(String url) {
		return imgHeader;
	}

    private boolean pageUrlMatchesPattern(String url) {
        String pageUrlPattern = "https://www.dailyshincho.jp/article/\\d*/\\d*/\\?all=1";
        return Pattern.matches(pageUrlPattern, url);
    }
}
