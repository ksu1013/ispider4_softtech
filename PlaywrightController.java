package extension.util;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.diquest.ispider.common.conf.main.BbsMain;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import extension.DefaultExtension;
import extension.LogUtil;

public class PlaywrightController {

    private BbsMain bbsMain;
    private LogUtil log;
    private String tmpFilesDirPath;	// 첨부파일 더미 파일 저장 경로

    private DefaultExtension defaultExtension = new DefaultExtension();

    public PlaywrightController(LogUtil log, BbsMain bbsMain) {
        this.log = log;
        this.bbsMain = bbsMain;
        tmpFilesDirPath = System.getenv("ISPIDER4_HOME") + "/attach/" + this.bbsMain.getBbsId() + "/tmp";
    }


    private boolean isCaptchaDetected(Page page, String url) {
        // 일반적인 캡차 요소들 (사이트 상황에 따라 선택자 추가 가능)
        boolean hasCaptchaId = page.locator("#captcha-container").isVisible();
//        boolean hasCaptchaFrame = page.locator("iframe[src*='captcha']").count() > 0 && page.locator("iframe[src*='captcha']").first().isVisible();
//        boolean hasCaptchaClass = page.locator(".g-recaptcha").isVisible() || page.locator("#captcha").isVisible();
//        boolean hasCaptchaUrl = page.url().contains("captcha");

//        if (hasCaptchaId || hasCaptchaFrame || hasCaptchaClass || hasCaptchaUrl) {
          if (hasCaptchaId ) {
//            log.error("!!! [수집 중단] 캡차 감지됨: " + url);

            // 메일 전송 함수 호출 (사용 중이신 메일 유틸리티 함수명을 여기에 적으세요)
            // 예: sendMailNotify("수집 엔진 캡차 알림", "다음 URL에서 캡차가 발생했습니다: " + url);
//            callAdminMailFunction(url);
            defaultExtension.sendTeamsAlarm();
            return true;
        }
        return false;
    }

    /**
     * 프로젝트 내부의 메일 발송 함수를 호출하는 브릿지 메서드
     */
//    private void callAdminMailFunction(String url) {
//        // 여기에 기존 프로젝트의 메일 발송 코드를 넣어주세요.
//        log.info("관리자에게 캡차 발생 알림 메일 발송을 요청했습니다.");
//    }

    // ==========================================
    // 2. [신규] 캡차 대응형 수집 메서드 (기존과 분리)
    // ==========================================

    /**
     * [캡차대응 버전] 캡차가 뜨면 즉시 중단하고 특정 문구를 반환합니다.
     */
    public String callChromeContentsWithCaptcha(String pageType, String port, String url) {
        String output = "";
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().connectOverCDP("http://127.0.0.1:" + port)) {

            BrowserContext context = browser.contexts().get(0);
            Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);

            // 이미지 로직은 기존 private 메서드로 분리해서 호출 (코드 중복 방지)
 //           setupImageDownloadLogic(page, pageType);

            try {
                page.navigate(url);
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(20000));

                // 캡차 체크 분기 처리
                if (isCaptchaDetected(page, url)) {
                    return "CAPTCHA_STOP_SIGNAL"; // 수집기에서 이 문자열을 받으면 처리를 멈추도록 설정
                }

            } catch (PlaywrightException e) {
                log.warn("페이지 로딩 지연 발생: " + e.getMessage());
            }

            output = page.content();
        } catch (Exception e) {
            log.error("Chrome 제어 오류", e);
        }
        return output;
    }

    /**
     * [캡차대응 버전] Shadow DOM 포함 수집
     */
    public String callChromeShadowDomWithCaptcha(String pageType, String port, String url, String cssSelector) {
        String output = "";
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().connectOverCDP("http://127.0.0.1:" + port)) {

            BrowserContext context = browser.contexts().get(0);
            Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);

 //           setupImageDownloadLogic(page, pageType);

            try {
                page.navigate(url);
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(20000));

                if (isCaptchaDetected(page, url)) {
                    return "CAPTCHA_STOP_SIGNAL";
                }
            } catch (PlaywrightException e) {
                log.warn("로딩 지연: " + e.getMessage());
            }

            output = page.content();
            Locator targetElements = page.locator(cssSelector);
            for (Locator targetElement : targetElements.all()) {
                output += "\n" + targetElement.evaluate("el => el.outerHTML");
            }
        } catch (Exception e) {
            log.error("Chrome 제어 오류", e);
        }
        return output;
    }



    /**
     * playwright로 chrome 브라우저를 컨트롤해서 페이지 크롤링 결과를 가져온다. (html 태그)
     *
     * @param pageType   페이지 타입 (일반적으로 LIST, CONTENT로 나뉘어져 있음)
     * @param url        수집 대상 url
     * @param port       크롬 원격 접속 PORT 값
     * @return 페이지 수집 결과
     */
    public String callChromeContents(String pageType, String port, String url) {
        String output = "";

        Page page = null;
        // try-with-resources 구문으로 브라우저 컨텍스트 자동 종료 보장
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().connectOverCDP("http://127.0.0.1:" + port)) {

            // 기존에 열려있는 탭을 쓰지 않고, 안전하게 제어하기 위해 현재 Context 가져오기
            BrowserContext context = browser.contexts().get(0);
            // 탭이 없으면 새로 만듦 (안전장치)
            page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);

//             --- [이미지 다운로드 로직 시작] ---
            if ("CONTENT".equalsIgnoreCase(pageType)) {
                Path outputDir = Paths.get(tmpFilesDirPath);
                if (!Files.exists(outputDir)) Files.createDirectories(outputDir);

                // 특정 이미지만 가져오기 위한 필터링 로직 추가
                page.onResponse(response -> {
                    try {
                        // 요청 성공(200)이고, 리소스 타입이 이미지인 경우만
                        if (response.ok() && "image".equals(response.request().resourceType())) {

                            // URL 필터링 (원하시는 확장자 로직 유지 또는 Content-Type 확인)
                            // 여기서는 간단히 Content-Type 헤더로 확장자 추론 예시
                            String contentType = response.headers().get("content-type");
                            if (contentType != null && contentType.contains("image")) {
                                String ext = "";
                                if (contentType.contains("jpg") || contentType.contains("jpeg")) {
                                    ext = ".jpg";
                                } else if (contentType.contains("png")) {
                                    ext = ".png";
                                } else if (contentType.contains("gif")) {
                                    ext = ".gif";
                                } else if (contentType.contains("webp")) {
                                    ext = ".webp";
                                }

                                String imageName = "";
                                String imageUrl = response.url();
                                // URL에서 쿼리 스트링(?, # 이후) 제거
                                int queryIndex = imageUrl.indexOf('?');
                                if (queryIndex != -1) {
                                    imageUrl = imageUrl.substring(0, queryIndex);
                                }
                                // 마지막 '/' 이후 문자열을 파일명으로 사용
                                int lastSlash = imageUrl.lastIndexOf('/');
                                if (lastSlash != -1 && lastSlash < imageUrl.length() - 1) {
                                    imageName = imageUrl.substring(lastSlash + 1);
                                    // URL 인코딩된 문자 디코딩 (예: %20 -> 공백)
                                    imageName = URLDecoder.decode(imageName, StandardCharsets.UTF_8.name());
                                }

                                if (
                                        !"".equals(imageName) && (imageName.endsWith(".jpg") || ext.equals(".jpg")
                                                || imageName.endsWith(".gif") || ext.equals(".gif")
                                                || imageName.endsWith(".png") || ext.equals(".png")
                                                || imageName.endsWith(".webp") || ext.equals(".webp"))
                                ) {

                                    // 저장 (비동기로 동작하므로 에러 발생 시 로그만 남김)
                                    byte[] body = response.body();
                                    Files.write(outputDir.resolve(imageName), body);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.info("이미지파일 저장 실패: " + response.url());
                        System.err.println(e.getMessage());
                    }
                });
            }
            // --- [이미지 다운로드 로직 끝] ---
//            setupImageDownloadLogic(page, pageType);
            // --- [페이지 이동 및 대기 로직 개선] ---
            try {
                // 페이지 이동
                page.navigate(url);
                // 스마트 대기: 네트워크 연결이 500ms 이상 멈출 때까지 대기 (최대 20초)
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(20000));
            } catch (PlaywrightException e) {
                log.warn("페이지 로딩이 일부 지연되었으나 HTML 추출을 시도합니다: " + e.getMessage());
            }

            output = page.content();	// 최종 렌더링된 HTML 전체 가져오기

        } catch (IOException e) {
            log.error("Chrome 제어 중 치명적 오류 발생", e);
        }

        return output;
    }

    /**
     * playwright로 chrome 브라우저를 컨트롤해서 페이지 크롤링 결과를 가져온다. (Shadow DOM 요소 가져오기 처리)
     *
     * @param pageType   페이지 타입 (일반적으로 LIST, CONTENT로 나뉘어져 있음)
     * @param url        수집 대상 url
     * @param port       크롬 원격 접속 PORT 값
     * @param cssSelector	ShadowDom 요소 선택자 (Shadow DOM 요소)
     * @return 페이지 수집 결과 (Shadow DOM 요소 포함)
     */
    public String callChromeShadowDomContents(String pageType, String port, String url, String cssSelector) {
        String output = "";
        Page page = null;
        // try-with-resources 구문으로 브라우저 컨텍스트 자동 종료 보장
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().connectOverCDP("http://127.0.0.1:" + port)) {

            // 기존에 열려있는 탭을 쓰지 않고, 안전하게 제어하기 위해 현재 Context 가져오기
            BrowserContext context = browser.contexts().get(0);

            // 탭이 없으면 새로 만듦 (안전장치)
            page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);

            // --- [이미지 다운로드 로직 시작] ---
            if ("CONTENT".equalsIgnoreCase(pageType)) {
                Path outputDir = Paths.get(tmpFilesDirPath);
                if (!Files.exists(outputDir)) Files.createDirectories(outputDir);

                // 특정 이미지만 가져오기 위한 필터링 로직 추가
                page.onResponse(response -> {
                    try {
                        // 요청 성공(200)이고, 리소스 타입이 이미지인 경우만
                        if (response.ok() && "image".equals(response.request().resourceType())) {

                            // URL 필터링 (원하시는 확장자 로직 유지 또는 Content-Type 확인)
                            // 여기서는 간단히 Content-Type 헤더로 확장자 추론 예시
                            String contentType = response.headers().get("content-type");
                            if (contentType != null && contentType.contains("image")) {
                                String ext = "";
                                if (contentType.contains("jpg") || contentType.contains("jpeg")) {
                                    ext = ".jpg";
                                } else if (contentType.contains("png")) {
                                    ext = ".png";
                                } else if (contentType.contains("gif")) {
                                    ext = ".gif";
                                } else if (contentType.contains("webp")) {
                                    ext = ".webp";
                                }

                                String imageName = "";
                                String imageUrl = response.url();
                                // URL에서 쿼리 스트링(?, # 이후) 제거
                                int queryIndex = imageUrl.indexOf('?');
                                if (queryIndex != -1) {
                                    imageUrl = imageUrl.substring(0, queryIndex);
                                }
                                // 마지막 '/' 이후 문자열을 파일명으로 사용
                                int lastSlash = imageUrl.lastIndexOf('/');
                                if (lastSlash != -1 && lastSlash < imageUrl.length() - 1) {
                                    imageName = imageUrl.substring(lastSlash + 1);
                                    // URL 인코딩된 문자 디코딩 (예: %20 -> 공백)
                                    imageName = URLDecoder.decode(imageName, StandardCharsets.UTF_8.name());
                                }

                                if (
                                        !"".equals(imageName) && (imageName.endsWith(".jpg") || ext.equals(".jpg")
                                                || imageName.endsWith(".gif") || ext.equals(".gif")
                                                || imageName.endsWith(".png") || ext.equals(".png")
                                                || imageName.endsWith(".webp") || ext.equals(".webp"))
                                ) {
                                    // 저장 (비동기로 동작하므로 에러 발생 시 로그만 남김)
                                    byte[] body = response.body();
                                    Files.write(outputDir.resolve(imageName), body);
                                }
                            }
                        }
                    } catch (PlaywrightException | IOException e) {
                        log.info("이미지파일 저장 실패: " + response.url());
                    }
                });
            }
            // --- [이미지 다운로드 로직 끝] ---
//            setupImageDownloadLogic(page, pageType);
            // --- [페이지 이동 및 대기 로직 개선] ---
            try {
                // 페이지 이동
                page.navigate(url);
                // 스마트 대기: 네트워크 연결이 500ms 이상 멈출 때까지 대기 (최대 20초)
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(20000));
            } catch (PlaywrightException e) {
                log.warn("페이지 로딩이 일부 지연되었으나 HTML 추출을 시도합니다: " + e.getMessage());
            }

            output = page.content();	// 최종 렌더링된 HTML 전체 가져오기
            Locator targetElements = page.locator(cssSelector);
            for (Locator targetElement : targetElements.all()) {
                output += "\n" + targetElement.evaluate("el => el.outerHTML");
            }
        } catch (IOException e) {
            log.error("Chrome 제어 중 치명적 오류 발생", e);
        }

        return output;
    }

    private void setupImageDownloadLogic(Page page, String pageType) throws IOException {
        // --- [이미지 다운로드 로직 시작] ---
        if ("CONTENT".equalsIgnoreCase(pageType)) {
            Path outputDir = Paths.get(tmpFilesDirPath);
            if (!Files.exists(outputDir)) Files.createDirectories(outputDir);

            // 특정 이미지만 가져오기 위한 필터링 로직 추가
            page.onResponse(response -> {
                try {
                    // 요청 성공(200)이고, 리소스 타입이 이미지인 경우만
                    if (response.ok() && "image".equals(response.request().resourceType())) {

                        // URL 필터링 (원하시는 확장자 로직 유지 또는 Content-Type 확인)
                        // 여기서는 간단히 Content-Type 헤더로 확장자 추론 예시
                        String contentType = response.headers().get("content-type");
                        if (contentType != null && contentType.contains("image")) {
                            String ext = "";
                            if (contentType.contains("jpg") || contentType.contains("jpeg")) {
                                ext = ".jpg";
                            } else if (contentType.contains("png")) {
                                ext = ".png";
                            } else if (contentType.contains("gif")) {
                                ext = ".gif";
                            } else if (contentType.contains("webp")) {
                                ext = ".webp";
                            }

                            String imageName = "";
                            String imageUrl = response.url();
                            // URL에서 쿼리 스트링(?, # 이후) 제거
                            int queryIndex = imageUrl.indexOf('?');
                            if (queryIndex != -1) {
                                imageUrl = imageUrl.substring(0, queryIndex);
                            }
                            // 마지막 '/' 이후 문자열을 파일명으로 사용
                            int lastSlash = imageUrl.lastIndexOf('/');
                            if (lastSlash != -1 && lastSlash < imageUrl.length() - 1) {
                                imageName = imageUrl.substring(lastSlash + 1);
                                // URL 인코딩된 문자 디코딩 (예: %20 -> 공백)
                                imageName = URLDecoder.decode(imageName, StandardCharsets.UTF_8.name());
                            }

                            if (
                                    !"".equals(imageName) && (imageName.endsWith(".jpg") || ext.equals(".jpg")
                                            || imageName.endsWith(".gif") || ext.equals(".gif")
                                            || imageName.endsWith(".png") || ext.equals(".png")
                                            || imageName.endsWith(".webp") || ext.equals(".webp"))
                            ) {
                                // 저장 (비동기로 동작하므로 에러 발생 시 로그만 남김)
                                byte[] body = response.body();
                                Files.write(outputDir.resolve(imageName), body);
                            }
                        }
                    }
                } catch (PlaywrightException | IOException e) {
                    log.info("이미지파일 저장 실패: " + response.url());
                }
            });
        }
        // --- [이미지 다운로드 로직 끝] ---
    }
}
