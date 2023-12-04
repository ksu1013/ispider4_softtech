package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.connect.ProxySetting;
import com.diquest.ispider.common.exception.ISpiderException;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.common.setting.SettingPath;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;
import com.diquest.ispider.core.util.FileUtil;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.List;
import java.util.Map;

public class ExternalFileDownloadExtension implements Extension {
	
	private static final int CONN_TIMEOUT = 1000;
	private static final int READ_TIMEOUT = 1000;
	private final static int BUF_SIZE = 1024;
	
	private String fileNameNodeId;
	private String fileUrlNodeId;
	
	private File downloadDir;
	
	public void startExtension(DqPageInfo dqPageInfo, String homePath) {
		Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
		
		// 이건 파일 명을 저장한 Node 이름을 채워 넣어주셔야 합니다.
		this.fileNameNodeId = reposit.getNodeByColumnName("FILE_NAME").getId();
		this.fileUrlNodeId = reposit.getNodeByColumnName("FILE_URL").getId();
		
		// ispider 설치 폴더 내 attach 폴더 아래 생성해주게 되므로 필요 시 변경해 주시면 됩니다.
		this.downloadDir = FileUtil.makeDir(dqPageInfo.getBbsId(), SettingPath.ATTACH_PATH);
	}
	
	public String changeRequestURL(String url, DqPageInfo dqPageInfo) { return null; }
	public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) { return null; }
	public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) { return null; }
	public List<String> makeNewUrls(String naviUrl, DqPageInfo dqPageInfo) { return null; }
	public Row[] splitRow(Row row, DqPageInfo dqPageInfo) { return null; }
	public void changeRowValue(Row row, DqPageInfo dqPageInfo) {}
	
	public boolean validData(Row row, DqPageInfo dqPageInfo) {
		String fileUrl = row.getNode(this.fileUrlNodeId).getValue();
		String fileName = row.getNode(this.fileNameNodeId).getValue();
		
		try {
			URL url = new URL(fileUrl);
			
			ProxySetting proxySetting = Configuration.getInstance().getBbsSetting(dqPageInfo.getBbsId()).getProxySetting();
			
			if(proxySetting != null) {
				HttpURLConnection conn;
				
				// Proxy 설정
				Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxySetting.getProxyHost(), proxySetting.getProxyPort()));
				
				// 연결 생성
				if (url.getProtocol().equalsIgnoreCase("https")) {
					conn = (HttpsURLConnection) url.openConnection(proxy);
				} else {
					conn = (HttpURLConnection) url.openConnection(proxy);
				}
				
				
				// 파일 다운로드 시 사용할 헤더 추가 (addonExtension 시 사용한 header 추가해주세요)
				conn.setRequestProperty("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36");
				
				// 기타 설정 추가
				conn.setReadTimeout(CONN_TIMEOUT);
				conn.setConnectTimeout(READ_TIMEOUT);
				conn.setDoOutput(true);
				
				// 연결
				int responseCode = conn.getResponseCode();
				if (responseCode == HttpURLConnection.HTTP_OK) {
					download(conn, fileName);
				} else {
					throw new IOException("Connection Fail. [status: " +responseCode+ "]");
				}
			} else {
				throw new ISpiderException("프록시 설정을 가져올 수 없습니다.");
			}
			
			return true;
		} catch(Exception e) {
			e.printStackTrace();
			return false;
		}
	}
	
	/**
	 * 파일 다운로드 시 사용하는 메소드
	 * @param conn HttpURLConnection
	 * @param fileName 파일 명
	 * @throws IOException 파일 작성 중 발생한 에러
	 */
	private void download(HttpURLConnection conn, String fileName) throws IOException {
		InputStream is = conn.getInputStream();
		
		FileOutputStream fw = null;
		try {
			byte[] buf = new byte[BUF_SIZE];
			fw = new FileOutputStream(new File(downloadDir, fileName));
			
			int nRead;
			while((nRead = is.read(buf, 0, BUF_SIZE)) > 0) {
				fw.write(buf, 0, nRead);
			}
		} finally {
			if(is != null) is.close();
			if(fw != null) fw.close();
		}
	}
	
	public void endExtension(DqPageInfo dqPageInfo) {}
}
