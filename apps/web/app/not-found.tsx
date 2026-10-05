import Link from "next/link";

export default function NotFound() {
  return (
    <div className="flex flex-col items-start gap-3 py-16">
      <h1 className="text-xl font-semibold">404</h1>
      <p className="text-sm text-muted-foreground">This page does not exist. / このページは存在しません。</p>
      <Link href="/" className="text-sm text-primary hover:underline">
        Dashboard →
      </Link>
    </div>
  );
}
