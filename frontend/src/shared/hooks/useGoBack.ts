import { useCallback } from 'react'
import { useLocation, useNavigate } from 'react-router'

/*
 * [이 파일이 하는 일]
 * "이전 화면으로 돌아가기". 앱 안에서 넘어온 화면이면 브라우저 기록을 한 칸 뒤로(-1) 간다.
 * 주소를 직접 입력했거나 새 탭으로 연 첫 화면이면 뒤로 갈 앱 기록이 없으므로(-1 은 사이트 밖으로 나감)
 * 대신 fallback 화면으로 간다 (기록을 쌓지 않게 replace).
 *
 *   const goBack = useGoBack('/mypage')   // 기록이 없으면 마이페이지로
 *   <button onClick={goBack}>뒤로</button>
 *
 * 주의: "돌아가기" 버튼에서 navigate('/이전화면') 처럼 이동하면 기록이 계속 쌓여서
 *       뒤로가기를 여러 번 눌러야 홈에 가게 된다. 돌아가는 동작에는 이 훅을 쓴다.
 */
export function useGoBack(fallback = '/') {
  const navigate = useNavigate()
  const location = useLocation()
  // react-router 는 이 탭에서 처음 연 화면의 key 를 'default' 로 둔다
  const canGoBack = location.key !== 'default'

  return useCallback(() => {
    if (canGoBack) navigate(-1)
    else navigate(fallback, { replace: true })
  }, [canGoBack, fallback, navigate])
}
