/** A code-native illustration used as a clearly labelled placeholder while listing photos are unavailable. */
export function Architecture({ compact = false }: { compact?: boolean }) {
  return <svg className={compact ? 'architecture compact' : 'architecture'} viewBox="0 0 600 440" fill="none" aria-hidden="true">
    <circle cx="334" cy="205" r="178" fill="#e0e9e0" />
    <path d="M54 371H551" stroke="#a8b8ad" strokeWidth="2" />
    <path d="M80 218L176 160L269 217V371H80V218Z" fill="#f8f5eb" stroke="#557366" strokeWidth="2" />
    <path d="M68 223L176 153L280 221" stroke="#315a49" strokeWidth="7" strokeLinejoin="round" />
    <path d="M239 94L390 68L459 108V371H239V94Z" fill="#ecede4" stroke="#557366" strokeWidth="2" />
    <path d="M390 68V371M240 113L390 86L459 126" stroke="#557366" strokeWidth="2" />
    {[0, 1, 2, 3, 4].map(row => <g key={row}>
      {[0, 1, 2].map(col => <path key={col} d={`M${260 + col * 42} ${132 + row * 43}v25h22v-29z`} fill="#91aea0" />)}
      <path d={`M410 ${137 + row * 42}v23l23 5v-25z`} fill="#6e9281" />
    </g>)}
    <path d="M157 303H194V371H157V303Z" fill="#a6b8aa" stroke="#557366" strokeWidth="2" />
    <path d="M107 242H144V280H107V242ZM202 242H239V280H202V242Z" fill="#c3d3c4" stroke="#557366" strokeWidth="2" />
    <path d="M326 331H355V371H326V331Z" fill="#557366" />
    <path d="M489 300V371M54 312V371" stroke="#315a49" strokeWidth="4" />
    <ellipse cx="488" cy="282" rx="34" ry="58" fill="#93af90" />
    <ellipse cx="54" cy="297" rx="24" ry="43" fill="#93af90" />
    <path d="M469 371H527M22 371H87" stroke="#315a49" strokeWidth="4" />
    <circle cx="142" cy="86" r="27" fill="#d9b685" />
    <path d="M87 133H112M99 121V145" stroke="#a4b4a5" strokeWidth="2" />
  </svg>;
}
