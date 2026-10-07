'use client';
export default function ErrorPage({ reset }: { error: Error & { digest?: string }; reset: () => void }) { return <section className="container empty-state error-page"><h1>Үйлчилгээ түр боломжгүй байна.</h1><p>Түр хүлээгээд дахин оролдоно уу.</p><button className="button" onClick={reset}>Дахин оролдох ↗</button></section>; }
