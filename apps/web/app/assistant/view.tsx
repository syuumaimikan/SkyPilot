"use client";

import { useRef, useState } from "react";
import { Send } from "lucide-react";
import { useMutation } from "@tanstack/react-query";
import { Alert, Badge, Button, Card, CardContent, Textarea, cn } from "@skypilot/ui";
import { ErrorView, PageHeader } from "@/components/common";
import { api, type ApiError } from "@/lib/api";
import { useI18n } from "@/lib/i18n";

interface ChatResponse {
  answer: string;
  deterministicAnswer: string;
  intent: string;
  grounded: boolean;
  facts: string[];
  llmUsed: boolean;
}

interface Msg {
  role: "user" | "assistant";
  text: string;
  meta?: ChatResponse;
}

export function AssistantView() {
  const { t, lang } = useI18n();
  const [msgs, setMsgs] = useState<Msg[]>([]);
  const [text, setText] = useState("");
  const bottom = useRef<HTMLDivElement>(null);
  const send = useMutation<ChatResponse, ApiError, string>({
    mutationFn: (message) => api.post<ChatResponse>("/api/assistant/chat", { message, lang }),
    onSuccess: (r) => {
      setMsgs((m) => [...m, { role: "assistant", text: r.answer, meta: r }]);
      setTimeout(() => bottom.current?.scrollIntoView({ behavior: "smooth" }), 50);
    },
  });
  const submit = () => {
    const m = text.trim();
    if (!m || send.isPending) return;
    setMsgs((x) => [...x, { role: "user", text: m }]);
    setText("");
    send.mutate(m);
  };
  const examples = [t("What should I do next?", "次に何をすればいい？"), t("How much is a Hyperion?", "Hyperionはいくら？"), t("What do I need for Terminator?", "Terminatorの条件は？"), t("How do I make money?", "どうやって稼げばいい？")];
  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-4">
      <PageHeader
        title={t("Assistant", "アシスタント")}
        description={t(
          "Answers come from your analysed profile and live market data. Prices, requirements, recipes, drop rates and XP are never taken from AI memory - if the data does not contain it, the answer is “I can't confirm that”.",
          "回答はあなたの分析済みプロフィールと最新の市場データから作られます。価格・条件・レシピ・ドロップ率・XPをAIの記憶で答えることはなく、データにない場合は「確認できません」と答えます。",
        )}
      />
      <Card>
        <CardContent className="flex min-h-80 flex-col gap-3 p-4">
          {msgs.length === 0 ? (
            <div className="flex flex-wrap gap-2">
              {examples.map((e) => (
                <button key={e} onClick={() => setText(e)} className="cursor-pointer rounded-md border border-border px-2.5 py-1 text-sm text-muted-foreground hover:bg-accent">
                  {e}
                </button>
              ))}
            </div>
          ) : null}
          {msgs.map((m, i) => (
            <div key={i} className={cn("max-w-[85%] rounded-lg px-3 py-2 text-sm whitespace-pre-wrap", m.role === "user" ? "self-end bg-primary text-primary-foreground" : "self-start bg-muted")}>
              {m.text}
              {m.meta ? (
                <div className="mt-2 flex flex-wrap items-center gap-1.5">
                  <Badge tone={m.meta.grounded ? "good" : "warn"}>{m.meta.grounded ? t("Grounded in your data", "データに基づく回答") : t("Not enough data", "データ不足")}</Badge>
                  {m.meta.llmUsed ? <Badge tone="info">{t("Phrased by Claude from the facts below", "下記の事実をClaudeが言い換え")}</Badge> : null}
                  {m.meta.facts.length > 0 ? (
                    <details className="w-full text-xs text-muted-foreground">
                      <summary className="cursor-pointer">{t("Facts used", "使用した事実")}</summary>
                      <ul className="mt-1 list-disc pl-4">
                        {m.meta.facts.map((f, j) => (
                          <li key={j}>{f}</li>
                        ))}
                      </ul>
                    </details>
                  ) : null}
                </div>
              ) : null}
            </div>
          ))}
          {send.isPending ? <div className="self-start rounded-lg bg-muted px-3 py-2 text-sm text-muted-foreground">…</div> : null}
          {send.error ? <ErrorView error={send.error} /> : null}
          <div ref={bottom} />
        </CardContent>
      </Card>
      <form
        className="flex items-end gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          submit();
        }}
      >
        <Textarea
          value={text}
          maxLength={500}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault();
              submit();
            }
          }}
          placeholder={t("Ask about your progression, prices or requirements…", "進行度・価格・条件について質問…")}
          className="min-h-12"
          aria-label={t("Message", "メッセージ")}
        />
        <Button type="submit" size="icon" disabled={!text.trim() || send.isPending} aria-label={t("Send", "送信")}>
          <Send />
        </Button>
      </form>
      <Alert className="text-xs">{t("Conversations are not stored on the server.", "会話内容はサーバーに保存されません。")}</Alert>
    </div>
  );
}
