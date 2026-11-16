# 틈틈 디자인 가이드 — 토끼·거북이 스티커 스크랩북

> 프론트 화면 디자인의 기준 문서. 화면 구조·기능은 [화면설계서 v2.0](틈틈%20화면설계서.md)이 기준이고, 이 문서는 **보이는 방식**만 정한다.
> 담당: 김용현 · 이슈 #60 · 이전 클레이 시안(#48)은 이 문서로 대체

## 1. 콘셉트

**"시간이 남아도는 토끼와, 여유롭게 계획하는 거북이의 동네 탐험"**

- **토끼**: 여유롭고 장난기 많은 즉흥파. 낮잠·간식·카페·산책을 좋아함. 선글라스가 트레이드마크
- **거북이**: 차분하고 똑똑한 탐험가. 지도·코스 순서·이동 시간을 챙김. 하늘색 탐험 모자 + 작은 배낭
- **둘은 동등한 공동 마스코트.** 거북이를 작게, 조연처럼, 멍청하게 그리지 않는다. 함께 나오면 머리 높이와 시각적 무게를 비슷하게 맞춘다
- 달리기 경주·승패 이야기는 쓰지 않는다
- 친근하고 유쾌하되 유아용·과하게 여성스러운 느낌은 피한다

## 2. 색과 글꼴 (코드: `frontend/src/index.css`)

| 역할 | 값 | Tailwind 클래스 | 쓰는 곳 |
|---|---|---|---|
| 메인 핑크 | `#F26FA8` | `primary` | 선택 테두리, 포인트 |
| 진한 핑크 | `#C93D7A` | `primary-dark` | 작은 핑크 글씨 (연한 배경에서 메인 핑크는 잘 안 읽힘) |
| 연한 핑크 | `#F9D5E3` | `primary-light` | 선택된 칩·탭 배경, 핑크 버튼 |
| 크림 | `#FFF8E9` | `cream` | 화면·카드 기본 배경 |
| 종이 베이지 | `#E9DFC9` | `paper` | 구분 영역, PC 바깥 여백 |
| 거북이 그린 | `#8CCB63` | `turtle` | 자연 카테고리 |
| 옐로우 | `#FFD84D` | `sunny` | **메인 CTA**, 별 |
| 스카이블루 | `#8BCBEA` | `sky` | 지도·이동 |
| 잉크 블랙 | `#202020` | `ink` | 글씨, 스티커형 버튼 외곽선 |
| 테두리 | `#E6DAC2` | `line` | 카드·칩 기본 테두리 |

- 크림이 기본, 나머지 색은 기능별 포인트. 한 화면에 모든 색을 세게 쓰지 않는다
- 글꼴: 본문 **Pretendard**(`font-sans`), 제목·버튼 **Jua**(`font-display`)

### 공통 클래스
| 클래스 | 모양 |
|---|---|
| `sticker-img` | 스티커 이미지 아래 짧은 그림자 (`StickerImage` 가 자동으로 붙임) |
| `paper-card` | 크림 배경 + 종이색 테두리 + 아래로 눌린 그림자 (카드) |
| `sticker-edge` | 잉크 외곽선 + 아래 그림자, 누르면 내려감 (버튼) |
| `torn-top` | 위쪽 가장자리를 물결 모양으로 자름 (하단바·바텀시트) |

## 3. 무엇을 이미지로, 무엇을 코드로

| 이미지 에셋 (직접 만들어 옴) | 코드로 그림 |
|---|---|
| 캐릭터, 장면 일러스트, 하단바 스티커, 카테고리·이동수단 스티커, 지도 핀, 장식, 종이 질감, 로고 | 버튼, 카드, 칩, 탭, 패널, 모든 글자, 작은 기능 아이콘(뒤로·하트·종·톱니·시계·검색·화살표) |

- **이미지 안에 글자를 넣지 않는다** (로고만 예외). 표지판·간판도 빈 칸으로 그린다 → 글자는 HTML 로 얹는다
- 지도 타일은 카카오 원본 그대로. 핀·현재 위치만 스티커로 바꾼다
- 모든 요소를 스티커로 만들지 않는다. 정보·버튼은 읽기 쉬운 UI 로 둔다

## 4. 목업과 다르게 가는 것 (우리 화면설계서 우선)

| 목업 | 우리 서비스 | 이유 |
|---|---|---|
| Next.js · shadcn/ui | **Vite + React + Tailwind 유지** | 확정 기술 스택 |
| 하단바: 홈·코스 추천·저장한 코스·마이페이지, 모든 화면 | **홈·주변 탐색·저장한 코스·마이(로그인)**, HOM-01·02 에만 | 화면설계서 v2.0 |
| 홈이 일러스트 화면 | **홈은 지도 유지**, 하단 시트에 문구·듀오 일러스트·노란 CTA | HOM-01 은 지도 기반 |
| 즐겨찾는 장소, 평점, 길찾기, 공유, 알림 설정, 추천 횟수, 내 외출 기록, 이용 가이드, 문의 | **넣지 않음** | 요구사항에 없는 기능 |
| 스플래시 화면 | 없음 (로고·휴식 장면은 로그인 화면 등에 활용) | 화면 14개에 없음 |

## 5. 에셋 넣는 법

1. 아래 6장 표의 **파일명 그대로** PNG(투명 배경) 또는 WebP 로 저장
2. `frontend/src/assets/stickers/{폴더}/` 에 넣는다 (예: `nav/nav-home.png`)
3. 끝. 코드 수정 없이 화면의 점선 자리표시가 이미지로 바뀐다 (`shared/assets/stickerAssets.ts` 가 자동으로 찾음)

- 새 에셋 종류를 추가할 때만 `stickerAssets.ts` 의 `STICKERS` 목록에 한 줄 추가
- 파일 크기: 1장 300KB 이하 권장 (큰 PNG 는 WebP 로 변환)

## 6. 에셋 목록 (35개)

우선순위: **P0** 셋팅 직후 바로 보이는 것 → **P1** 화면별 적용 때 → **P2** 있으면 좋음

### 0단계: 캐릭터 기준 시트 (앱에 안 들어감, 먼저 1번 만들기)
그림체가 화면마다 달라지지 않게, 이 3장을 먼저 뽑고 **이후 모든 생성에 참고 이미지로 첨부**한다. 완성본: [`docs/design-refs/`](design-refs/) (앱 폴더에 넣지 않음)

| 이름 | 내용 |
|---|---|
| ref-rabbit | 토끼 기준 시트: 정면·측면·뒷모습 + 표정 4개(무심·신남·졸림·장난) |
| ref-turtle | 거북이 기준 시트: 정면·측면·뒷모습 + 표정 4개(자신감·생각·기쁨·집중) |
| ref-duo | 둘이 나란히 선 크기 비교 (머리 높이 같게) |

### 캐릭터 · 장면
| 파일 | 크기 | 쓰는 곳 | 우선 |
|---|---|---|---|
| ~~`mascot/rabbit-head.png`~~ | - | 마이페이지 프로필, 조건 설정 하단 버튼 왼쪽에서 빼꼼 → **`nav/nav-home.png` 재사용** | 완료 |
| ~~`mascot/turtle-head.png`~~ | - | 조건 설정 하단 버튼 오른쪽에서 빼꼼, 프로필 선택지 → **`nav/nav-my.png` 재사용** | 완료 |
| `mascot/turtle-peek.png` | 1024² | 마이페이지 메뉴 모서리 장식 | P2 |
| `scene/scene-home.png` | 1536×1024 | 홈 하단 시트 (HOM-01) | P0 |
| `scene/scene-empty.png` | 1536×1024 | 추천 결과 없음 (RES-01) | P0 |
| `scene/scene-course.png` | 1536×1024 | 코스 상세 상단 배너 (DET-01, 사진 없을 때) | P1 |
| `scene/scene-resting.png` | 1536×1024 | 저장한 코스 목록 하단·빈 목록, 추천 만료(2시간) 안내 | P1 |
| `scene/scene-relax.png` | 1536×1024 | 로그인·회원가입 상단 | P1 |
| `scene/scene-loading.png` | 1536×1024 | 공통 로딩 (추천 계산 중) | P1 |

### 하단바 (36px 로 작게 보임 → 단순하고 굵게)
| 파일 | 크기 | 그림 | 우선 |
|---|---|---|---|
| `nav/nav-home.png` | 512² | 토끼 얼굴 (선글라스) | P0 |
| `nav/nav-nearby.png` | 512² | 핑크 지도 핀 | P0 |
| `nav/nav-saved.png` | 512² | 노란 별 | P0 |
| `nav/nav-my.png` | 512² | 거북이 얼굴 (모자) | P0 |

### 대분류 8개 (조건 설정 카테고리 버튼, 사진 없는 장소 썸네일, 지도 마커 안쪽)
| 파일 | 그림 | 우선 |
|---|---|---|
| `category/category-culture.png` | 영화 티켓 + 작은 액자 | P0 |
| `category/category-cafe.png` | 김 나는 커피잔 | P0 |
| `category/category-food.png` | 밥그릇 + 수저 | P0 |
| `category/category-nature.png` | 둥근 나무 + 풀 | P0 |
| `category/category-entertainment.png` | 게임패드 + 주사위 | P0 |
| `category/category-shopping.png` | 쇼핑백 | P0 |
| `category/category-sports.png` | 볼링공 + 핀 | P0 |
| `category/category-tourism.png` | 카메라 (또는 서울 타워) | P0 |

모두 512². 같은 크기·같은 각도(살짝 위에서 본 정면)로 맞춘다.

### 이동수단 3개 (조건 설정 이동수단 버튼)
| 파일 | 그림 | 우선 |
|---|---|---|
| `transport/transport-transit.png` | 초록 버스 앞모습 | P1 |
| `transport/transport-walk.png` | 운동화 한 켤레 | P1 |
| `transport/transport-car.png` | 노란 자동차 앞모습 | P1 |

### 지도
| 파일 | 크기 | 그림 | 우선 |
|---|---|---|---|
| `map/map-pin.png` | 512² | 핑크 핀, 가운데 흰 원(비워 둠 → 순번·카테고리를 코드로 얹음). 핀 끝이 캔버스 아래 가운데 | P1 |
| `map/map-me.png` | 512² | 토끼·거북이가 붙어 손 흔드는 작은 스티커 (현재 위치) | P1 |
| `map/map-flag.png` | 512² | 핑크 깃발 (코스 도착·종료) | P1 |

### 장식 · 질감 · 로고
| 파일 | 크기 | 그림 | 우선 |
|---|---|---|---|
| `texture/texture-paper.png` | 1024² | **투명 아님.** 크림색 종이 질감, 이어 붙여도 경계 없는 타일 | P0 |
| `deco/deco-star.png` | 512² | 노란 별 | P1 |
| `deco/deco-sparkle.png` | 512² | 반짝이 3개 묶음 | P1 |
| `deco/deco-cloud.png` | 512² | 흰 구름 | P1 |
| `deco/deco-tape-pink.png` | 512×256 | 핑크 마스킹테이프 조각 (반투명, 가장자리 찢김) | P1 |
| `deco/deco-tape-yellow.png` | 512×256 | 노란 마스킹테이프 조각 | P1 |
| ~~`deco/deco-lines.png`~~ | - | 강조 낙서선 `///` → **코드(SVG)로 그림** (단순한 선이라 이미지 불필요) | 제외 |
| `deco/deco-note.png` | 512² | 음표 | P1 |
| `brand/brand-logo.png` | 1024×768 | 구름 모양 엠블럼 안 "틈틈" + 아래 노란 리본 "TeumTeum" (**글자 들어가는 유일한 에셋**) | P2 |

## 7. 생성 프롬프트

모든 프롬프트 = **[공통 스타일]** + (캐릭터가 나오면 **[토끼]·[거북이]**) + **[개별 내용]**. 0단계 기준 시트를 참고 이미지로 함께 첨부한다.

### [공통 스타일]
```
Hand-drawn sticker illustration in a playful sticker-scrapbook style.
Bold clean black outlines of consistent medium weight, flat colors with soft cel shading,
a thick white die-cut sticker border around the whole subject, a short soft drop shadow,
very subtle paper texture. Limited palette: pink #F26FA8, light pink #F9D5E3, cream #FFF8E9,
turtle green #8CCB63, yellow #FFD84D, sky blue #8BCBEA, ink black #202020.
Friendly and witty, clean and modern, NOT childish, NOT overly girly, NOT 3D clay.
Transparent background. Absolutely no text, letters, numbers or watermark.
```

### [토끼]
```
Rabbit character: white-ivory body, long upright ears with pink inner ears, simple round silhouette,
black sunglasses as signature item, small pink cheeks, relaxed and mischievous vibe.
```

### [거북이]
```
Turtle character: equal co-mascot with the same height and visual weight as the rabbit.
Pale yellow-green skin, big round domed green (#8CCB63) shell with a simple hexagon pattern,
sky-blue explorer cap with a yellow brim, small green backpack, bright confident smart eyes.
Calm, clever explorer — never dumb or sluggish.
```

### 0단계 기준 시트
| 이름 | 개별 내용 |
|---|---|
| ref-rabbit | `Character reference sheet of the rabbit: front, side and back views, plus four expression heads (deadpan, excited, sleepy, playful). Plain cream background, evenly spaced.` |
| ref-turtle | `Character reference sheet of the turtle: front, side and back views, plus four expression heads (confident, thinking, happy, focused). Plain cream background, evenly spaced.` |
| ref-duo | `The rabbit and the turtle standing side by side, full body, same head height, neutral pose, size comparison sheet. Plain cream background.` |

### 캐릭터 · 장면
| 파일 | 개별 내용 |
|---|---|
| rabbit-head | `Head-and-shoulders sticker of the rabbit, front view, cool relaxed smile, sunglasses. Centered, square canvas.` |
| turtle-head | `Head-and-shoulders sticker of the turtle wearing its cap, front view, confident friendly smile. Centered, square canvas.` |
| turtle-peek | `The turtle peeking up from the bottom-right corner, only head, cap and top of shell visible, small sparkles around, cheerful.` |
| scene-home | `The rabbit and the turtle side by side ready to go out: rabbit with a small backpack and sunglasses, turtle holding a folded map. Behind them a wooden signpost with three BLANK arrow signs, a few small trees, and a simple pastel city skyline with a tower. Wide 3:2 composition, characters equal size in the center-right.` |
| scene-empty | `The rabbit and the turtle looking at a big unfolded map together, puzzled but positive, small exclamation doodle lines above. Wide 3:2, centered, lots of breathing room.` |
| scene-course | `The rabbit sipping an iced drink in a park wearing sunglasses, the turtle next to it holding a drink and smiling, trees and a light city skyline behind. Wide 3:2 banner.` |
| scene-resting | `The rabbit lying on its back listening to music with a music note floating, the turtle napping peacefully beside it on the grass. Wide 3:2, horizontal, calm.` |
| scene-relax | `The rabbit relaxing on a pink striped beach chair with an iced drink and sunglasses, the turtle sitting next to it reading a map. Wide 3:2.` |
| scene-loading | `The rabbit hopping and the turtle walking briskly side by side on a short dotted path, both cheerful, motion doodle lines. Wide 3:2, horizontal strip.` |

### 하단바 · 카테고리 · 이동수단 · 지도 · 장식
작은 크기에서도 알아보도록 **"simple, bold, minimal details, centered, square canvas"** 를 꼭 붙인다.

| 파일 | 개별 내용 |
|---|---|
| nav-home | `Small icon sticker: the rabbit's face with sunglasses, front view.` |
| nav-nearby | `Small icon sticker: a pink map location pin.` |
| nav-saved | `Small icon sticker: a chubby yellow star.` |
| nav-my | `Small icon sticker: the turtle's face with its sky-blue cap, front view.` |
| category-culture | `Icon sticker: a movie ticket and a small picture frame.` |
| category-cafe | `Icon sticker: a coffee cup with steam.` |
| category-food | `Icon sticker: a Korean rice bowl with a spoon and chopsticks.` |
| category-nature | `Icon sticker: a round green tree with a little grass.` |
| category-entertainment | `Icon sticker: a game controller and a die.` |
| category-shopping | `Icon sticker: a pink shopping bag.` |
| category-sports | `Icon sticker: a bowling ball and a bowling pin.` |
| category-tourism | `Icon sticker: a retro camera.` |
| transport-transit | `Icon sticker: a green city bus, front view.` |
| transport-walk | `Icon sticker: a pair of sneakers.` |
| transport-car | `Icon sticker: a small yellow car, front view.` |
| map-pin | `Map pin sticker: pink teardrop pin with an EMPTY white circle in the middle, pin tip at the bottom center of the canvas.` |
| map-me | `Tiny sticker of the rabbit and the turtle squeezed together waving, heads only.` |
| map-flag | `Icon sticker: a small pink flag on a pole.` |
| deco-star | `Doodle sticker: a single yellow star.` |
| deco-sparkle | `Doodle sticker: three small sparkles in a cluster.` |
| deco-cloud | `Doodle sticker: a fluffy white cloud.` |
| deco-tape-pink | `A strip of semi-transparent pink masking tape with torn edges, 2:1 canvas, no white sticker border.` |
| deco-tape-yellow | `A strip of semi-transparent yellow masking tape with torn edges, 2:1 canvas, no white sticker border.` |
| deco-lines | `Doodle: three short black emphasis strokes like ///, no white sticker border.` |
| deco-note | `Doodle sticker: a black music note.` |
| texture-paper | `Seamless tileable cream (#FFF8E9) paper texture, very subtle fibers and grain, flat lighting, NO transparency, no objects.` (공통 스타일 없이 이것만) |
| brand-logo | `Logo emblem: a white fluffy cloud shape with bold black outline containing the Korean word "틈틈" in thick rounded black letters, a yellow ribbon banner below with "TeumTeum", small sparkles. Transparent background.` (글자 허용) |

## 8. 체크리스트
- [ ] 토끼와 거북이가 동등한 크기·존재감인가
- [ ] 거북이가 작거나 멍청한 조연처럼 보이지 않는가
- [ ] 모든 에셋의 선 두께·흰 테두리·그림자 방향이 같은가
- [ ] 이미지 안에 글자가 없는가 (로고 제외)
- [ ] 지도·주요 버튼이 장식에 가려지지 않는가
- [ ] 핑크 일변도·유아용으로 흐르지 않았는가
- [ ] 모바일에서 터치 영역이 충분한가 (버튼 높이 44px 이상)
