package extension;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;

/**
 * PXCOUNCIL
 * @author 이가원
 *
 */
public class CouncilExtension implements Extension{
	private String cl_cd;
	private String origin_cd;
	private ConnectionUtil connectionUtil = new ConnectionUtil();
	private boolean error_exist = false;
	private int doc_id;
	private String now_time;
	private List<HashMap<String, String>> attaches_info;
	private String file_name;
	
	@Override
	public void startExtension(DqPageInfo dqPageInfo, String homePath) {
		System.out.println("StartExtension!!!!!");
		Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
		doc_id = 0;
		attaches_info = new ArrayList<>();
		
		Date now = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
		now_time = sdf.format(now);
	}

	@Override
	public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
		
		return url;
	}
	
	@Override
	public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
		Map<String, String> map = new HashMap<String, String>();
		return map;
	}
	
	@Override
	public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
		if(dqPageInfo.getParentUrl()!=null && !dqPageInfo.getParentUrl().equals("")) {
			Document document = Jsoup.parse(htmlSrc);
			String title = document.getElementsByClass("singlePageTitle").text();
			String datetime = document.getElementsByAttributeValue("property", "article:published_time").attr("content");
			Element content_ele =document.getElementsByClass("post").get(0);
			content_ele.getElementsByClass("singlePageTitle").remove();
			content_ele.getElementsByClass("midColPostMeta").remove();
			content_ele.getElementById("entryMeta").remove();
			
			htmlSrc = "<cont_title>"+title+"</cont_title>"+content_ele.outerHtml()+"<cont_datetime>"+datetime+"</cont_datetime>";
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

			//날짜 바꾸기
			if (nodeName.equals("created_date") && !nodeValue.equals("") && nodeValue!=null) {
				row.getNodeByIdx(i).setValue(nodeValue.replace("T", " "));
			}
			
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
		//현재 문서 첨부파일
		List<String> content_images = new ArrayList<>();
		
		try {
			String title="";
			
			for (int i = 0; i < row.size(); i++) {
				String nodeId = row.getNodeByIdx(i).getId();
				String nodeName = row.getNodeByIdx(i).getName();
				String nodeValue = row.getNodeByIdx(i).getValue();

				if (nodeName.equals("title") ) {
					title = nodeValue;
				}
				
				
				if(nodeName.equals("images") && nodeValue != null && !nodeValue.equals("")) {
					HashMap<String, String> attach = new HashMap<>();
					
					content_images = connectionUtil.setAttachImageValue(attaches_info, attach, nodeValue, doc_id);
					
					file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
					
					//첨부파일명 저장
					String attachFiles = connectionUtil.setAttachNode(file_name, attach);
					System.out.println(attachFiles);
					row.getNodeByIdx(i).clearValue();
					row.getNodeByIdx(i).setValue(attachFiles);
				}
			}
			
			//=====================================================
			//현재 문서 첨부파일 매핑 위치 설정
			for(int i=0; i<row.size(); i++) {
				String nodeId = row.getNodeByIdx(i).getId();
				String nodeName = row.getNodeByIdx(i).getName();
				String nodeValue = row.getNodeByIdx(i).getValue();
				List<String> image_list = new ArrayList<>();		//본문에서 추출한 이미지 태그 목록
				
				if(nodeName.equals("content") && nodeValue != null && !nodeValue.equals("")) {
					String content = connectionUtil.getContainImageContent(nodeValue, content_images);
					
					row.getNodeByIdx(i).clearValue();
					row.getNodeByIdx(i).setValue(content);
				}
			}
			//=====================================================
			
			if(title.equals("")) {
				isCheck = false;
				connectionUtil.upFailDocFileDownloadCount();	//에러 파일수 판단용
			}
			
		}catch(Exception e) {
			e.printStackTrace();
		}
		return isCheck;
	}

	@Override
	public void endExtension(DqPageInfo dqPageInfo) {
		file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
		String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);

		// 수집로그 저장
		connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);

		connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name);

		System.out.println("첨부파일 목록 : " + attaches_info.toString());

		// 첨부파일 저장
		connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info);
	}
	
}
