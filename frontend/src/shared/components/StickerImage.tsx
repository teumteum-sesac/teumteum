import { getStickerUrl, STICKERS, type StickerKey } from '../assets/stickerAssets'

/*
 * [이 파일이 하는 일]
 * 스티커 에셋을 이름으로 그린다. 파일이 아직 없으면 같은 크기의 점선 자리표시를 대신 그린다.
 *   <StickerImage name="sceneEmpty" className="w-56" />
 *
 * 크기는 className 으로 정한다 (w-·size-·h- 등). 자리표시도 같은 크기를 차지하므로
 * 에셋이 들어와도 레이아웃이 밀리지 않는다. 자리표시 비율은 ratio 로 맞춘다 (기본 정사각형).
 */
interface StickerImageProps {
  name: StickerKey
  className?: string
  /** 자리표시 비율 (에셋 비율과 맞추기). 예: 'aspect-[3/2]' */
  ratio?: string
  /** 의미 있는 이미지면 설명을 넣는다. 장식이면 비워 둔다(화면 읽기 프로그램이 건너뜀) */
  alt?: string
}

export default function StickerImage({ name, className = 'size-12', ratio = 'aspect-square', alt = '' }: StickerImageProps) {
  const url = getStickerUrl(name)

  if (url) {
    // sticker-img: 스티커 아래 짧은 그림자 (index.css). 흰 외곽선은 이미지 자체에 들어 있다
    return <img src={url} alt={alt} draggable={false} className={`sticker-img object-contain ${className}`} />
  }

  return (
    <div
      aria-hidden={!alt}
      aria-label={alt || undefined}
      title={`에셋 준비 중: ${STICKERS[name].file}`}
      className={`flex shrink-0 items-center justify-center overflow-hidden rounded-xl border-2 border-dashed border-paper bg-paper/30 ${ratio} ${className}`}
    >
      {/* 개발 중에만 어떤 에셋 자리인지 보여 준다 */}
      {import.meta.env.DEV && (
        <span className="px-0.5 text-center text-[9px] leading-tight text-neutral-400">{STICKERS[name].label}</span>
      )}
    </div>
  )
}
