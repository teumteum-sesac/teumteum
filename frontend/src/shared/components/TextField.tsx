import type { InputHTMLAttributes } from 'react'

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  /** 입력란 아래 오류 안내 */
  error?: string
  /** 입력란 아래 일반 안내 */
  hint?: string
}

export default function TextField({ label, error, hint, id, className = '', ...props }: TextFieldProps) {
  const inputId = id ?? props.name
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={inputId} className="px-1 text-sm font-bold">
        {label}
      </label>
      <input
        id={inputId}
        className={`h-12 rounded-2xl border-2 bg-cream px-4 text-sm outline-none placeholder:text-neutral-400 focus:border-primary disabled:bg-paper/50 disabled:text-neutral-400 ${
          error ? 'border-red-400' : 'border-line'
        } ${className}`}
        {...props}
      />
      {error ? <p className="px-1 text-xs text-red-500">{error}</p> : hint && <p className="px-1 text-xs text-neutral-400">{hint}</p>}
    </div>
  )
}
