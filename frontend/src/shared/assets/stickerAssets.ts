import type { CategoryCode, TransportType } from '../types/domain'

/*
 * [이 파일이 하는 일]
 * 토끼·거북이 스티커 이미지 에셋의 "목록표". 화면에서는 파일 경로 대신 이름(StickerKey)으로 부른다.
 *   <StickerImage name="navHome" />
 *
 * [파일이 없어도 깨지지 않는 이유]
 *   아래 import.meta.glob 이 src/assets/stickers/ 안에 "실제로 있는" 이미지만 모아 온다.
 *   목록표에는 있는데 파일이 아직 없으면 getStickerUrl 이 null 을 돌려주고, StickerImage 가 자리표시(점선 박스)를 그린다.
 *   → 에셋을 만들어 정해진 이름으로 폴더에 넣기만 하면 코드 수정 없이 화면에 바로 나온다.
 *
 * [왜 public/ 이 아니라 src/assets/ 인가]
 *   public/ 파일은 빌드 도구가 존재 여부를 모른다(없는 파일을 참조해도 그냥 404).
 *   src/assets/ 에 두면 Vite 가 파일을 확인하고, 이름에 해시를 붙여 캐시 문제도 막아 준다.
 *
 * 에셋 규격·프롬프트: docs/design-sticker.md
 */

// src/assets/stickers/ 아래 png·webp 를 모두 찾아 { '../../assets/stickers/nav/nav-home.png': '/assets/nav-home-해시.png' } 형태로 받는다
const FILES = import.meta.glob<string>('../../assets/stickers/**/*.{png,webp}', {
  eager: true,
  query: '?url',
  import: 'default',
})

// 'nav/nav-home' 처럼 폴더/파일명(확장자 제외)으로 다시 찾기 쉽게 바꿔 둔다
const URL_BY_PATH = new Map(
  Object.entries(FILES).map(([path, url]) => [path.replace(/^.*\/stickers\//, '').replace(/\.(png|webp)$/, ''), url]),
)

/** 에셋 목록. file = src/assets/stickers/ 기준 경로(확장자 제외), label = 자리표시에 보일 설명 */
export const STICKERS = {
  // 캐릭터 얼굴 (프로필, 조건 설정 하단 버튼 양옆 등). 하단바 얼굴 스티커를 같이 쓴다 (128px 정도까지 선명)
  rabbitHead: { file: 'nav/nav-home', label: '토끼 얼굴' },
  turtleHead: { file: 'nav/nav-my', label: '거북이 얼굴' },
  turtlePeek: { file: 'mascot/turtle-peek', label: '모서리 거북이' },

  // 장면 일러스트
  sceneHome: { file: 'scene/scene-home', label: '홈 듀오' },
  sceneRelax: { file: 'scene/scene-relax', label: '휴식 듀오' },
  sceneCourse: { file: 'scene/scene-course', label: '코스 듀오' },
  sceneEmpty: { file: 'scene/scene-empty', label: '결과 없음' },
  sceneResting: { file: 'scene/scene-resting', label: '낮잠 듀오' },
  sceneLoading: { file: 'scene/scene-loading', label: '로딩' },

  // 하단바
  navHome: { file: 'nav/nav-home', label: '홈' },
  navNearby: { file: 'nav/nav-nearby', label: '주변' },
  navSaved: { file: 'nav/nav-saved', label: '저장' },
  navMy: { file: 'nav/nav-my', label: '마이' },

  // 대분류 8개 (조건 설정 카테고리, 사진 없는 장소 썸네일, 지도 마커 안쪽)
  categoryCulture: { file: 'category/category-culture', label: '문화' },
  categoryCafe: { file: 'category/category-cafe', label: '카페' },
  categoryFood: { file: 'category/category-food', label: '음식' },
  categoryNature: { file: 'category/category-nature', label: '자연' },
  categoryEntertainment: { file: 'category/category-entertainment', label: '오락' },
  categoryShopping: { file: 'category/category-shopping', label: '쇼핑' },
  categorySports: { file: 'category/category-sports', label: '스포츠' },
  categoryTourism: { file: 'category/category-tourism', label: '관광' },

  // 이동수단 3개
  transportTransit: { file: 'transport/transport-transit', label: '대중교통' },
  transportWalk: { file: 'transport/transport-walk', label: '도보' },
  transportCar: { file: 'transport/transport-car', label: '차량' },

  // 지도
  mapPin: { file: 'map/map-pin', label: '핀' },
  mapMe: { file: 'map/map-me', label: '현재 위치' },
  mapFlag: { file: 'map/map-flag', label: '도착' },

  // 장식
  decoStar: { file: 'deco/deco-star', label: '별' },
  decoSparkle: { file: 'deco/deco-sparkle', label: '반짝' },
  decoCloud: { file: 'deco/deco-cloud', label: '구름' },
  decoTapePink: { file: 'deco/deco-tape-pink', label: '테이프' },
  decoTapeYellow: { file: 'deco/deco-tape-yellow', label: '테이프' },
  decoNote: { file: 'deco/deco-note', label: '음표' },

  // 배경 질감 (투명 아님, 이어 붙여도 경계가 안 보이는 타일)
  texturePaper: { file: 'texture/texture-paper', label: '종이' },

  // 로고 (이미지 안에 글자가 들어가는 유일한 에셋)
  brandLogo: { file: 'brand/brand-logo', label: '로고' },
} as const

export type StickerKey = keyof typeof STICKERS

/** 실제 파일 URL. 파일이 아직 없으면 null */
export function getStickerUrl(key: StickerKey): string | null {
  return URL_BY_PATH.get(STICKERS[key].file) ?? null
}

/** 대분류 → 스티커 */
export const CATEGORY_STICKER: Record<CategoryCode, StickerKey> = {
  CULTURE: 'categoryCulture',
  CAFE: 'categoryCafe',
  FOOD: 'categoryFood',
  NATURE: 'categoryNature',
  ENTERTAINMENT: 'categoryEntertainment',
  SHOPPING: 'categoryShopping',
  SPORTS: 'categorySports',
  TOURISM: 'categoryTourism',
}

/** 이동수단 → 스티커 */
export const TRANSPORT_STICKER: Record<TransportType, StickerKey> = {
  TRANSIT: 'transportTransit',
  WALK: 'transportWalk',
  CAR: 'transportCar',
}

/** 종이 질감 배경 style. 화면 배경·하단 패널·하단바처럼 크림색 면에 같이 쓴다 (질감 파일이 없으면 undefined → 단색 크림) */
const paperUrl = getStickerUrl('texturePaper')
export const PAPER_STYLE = paperUrl ? { backgroundImage: `url(${paperUrl})`, backgroundSize: '256px' } : undefined
