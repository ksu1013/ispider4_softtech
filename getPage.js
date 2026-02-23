const CDP = require('chrome-remote-interface');

const port = process.argv[2];
const url = process.argv[3]; // 인덱스 2의 인수를 url 변수에 할당합니다.
// const outputFilePath = process.argv[4]; // 저장할 파일 경로와 이름을 지정합니다.

(async function() {
    // Chrome에 원격으로 연결합니다.
    const client = await CDP({ port });

    // 디버그 타겟 목록을 가져옵니다.
    const { Target, Page } = client;
    const { targetInfos } = await Target.getTargets();

    // 첫 번째 탭을 대상으로 선택합니다.
    const targetId = targetInfos[0].targetId;
    await Target.activateTarget({ targetId });

    // 원하는 URL에 접속합니다.
    await Page.navigate({ url });

    // 일정 시간(10초) 동안 기다립니다.
    await new Promise(resolve => setTimeout(resolve, 10000));

    // 페이지 소스를 가져옵니다.
    const { DOM } = client;
    const documentNodeId = (await DOM.getDocument()).root.nodeId;
    const html = (await DOM.getOuterHTML({ nodeId: documentNodeId })).outerHTML;

    // // 페이지 소스를 파일로 저장합니다.
    // fs.writeFileSync(outputFilePath, html);
    //
    // console.log(`페이지 소스가 ${outputFilePath} 파일로 저장되었습니다.`);
    console.log(html);

    // 연결을 종료합니다.
    await client.close();
})();