interface LogoProps {
  size?: 'sm' | 'default' | 'lg';
  className?: string;
  showTagline?: boolean;
}

const sizeConfig = {
  sm: 'h-7',
  default: 'h-9',
  lg: 'h-14 sm:h-16',
} as const;

export function Logo({ size = 'default', className, showTagline = false }: LogoProps) {
  return (
    <span className={`inline-flex flex-col ${className ?? ''}`}>
      <img
        src="/logo.svg"
        alt="지금이니?!"
        className={`${sizeConfig[size]} w-auto`}
      />
      {showTagline && (
        <span className="mt-1 text-sm text-fg-muted">
          AI가 읽어주는 미국 주식 시그널
        </span>
      )}
    </span>
  );
}
