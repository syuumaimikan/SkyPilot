import type { Metadata } from "next";
import { PhonePlay } from "@/components/phone-play";
export const metadata: Metadata = { title: "Play on Phone · スマホでプレイ" };
export default function Page() { return <PhonePlay/>; }
