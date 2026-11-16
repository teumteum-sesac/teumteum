import type { ButtonHTMLAttributes } from 'react'

/*
 * 공통 버튼 (스티커 스타일: 잉크 외곽선 + 아래 그림자, 제목 폰트)
 *   primary   노란색. 화면의 가장 중요한 버튼 1개 (외출 코스 추천하기, 코스 상세 보기, 조건 수정하기)
 *   pink      핑크. 저장·추천받기처럼 "확정" 느낌의 버튼 (이 코스 저장하기, 추천받기)
 *   secondary 크림색. 보조 버튼 (다시 추천하기, 취소)
 */
interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'pink' | 'secondary'
}

const VARIANT_CLASS = {
  primary: 'bg-sunny',
  pink: 'bg-primary-light',
  secondary: 'bg-cream',
}

export default function Button({ variant = 'primary', className = '', type = 'button', ...props }: ButtonProps) {
  return (
    <button
      type={type}
      className={`sticker-edge h-12 w-full rounded-2xl font-display text-[17px] text-ink transition-transform disabled:border-line disabled:bg-paper disabled:text-neutral-400 disabled:shadow-none ${VARIANT_CLASS[variant]} ${className}`}
      {...props}
    />
  )
}
