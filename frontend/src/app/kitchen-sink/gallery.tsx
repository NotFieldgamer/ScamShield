"use client";

import * as React from "react";
import { Button } from "@/components/primitives/Button";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogTitle,
  DialogTrigger,
} from "@/components/primitives/Dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/primitives/DropdownMenu";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/primitives/Tabs";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/primitives/Tooltip";
import { Toast, ToastClose, ToastDescription, ToastTitle } from "@/components/primitives/Toast";
import { Slider } from "@/components/primitives/Slider";
import { Switch } from "@/components/primitives/Switch";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/primitives/Popover";
import { Progress } from "@/components/primitives/Progress";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/primitives/Accordion";
import { ScrollArea } from "@/components/primitives/ScrollArea";
import { Separator } from "@/components/primitives/Separator";
import { ToggleGroup, ToggleGroupItem } from "@/components/primitives/ToggleGroup";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/primitives/Avatar";
import { GlassCard } from "@/components/glass/GlassCard";
import { UvSweep } from "@/components/glass/UvSweep";

const AVATAR_SRC =
  "data:image/svg+xml,%3Csvg%20xmlns='http://www.w3.org/2000/svg'%20width='40'%20height='40'%3E%3Cdefs%3E%3ClinearGradient%20id='g'%20x1='0'%20y1='0'%20x2='1'%20y2='1'%3E%3Cstop%20offset='0'%20stop-color='%239b8cff'/%3E%3Cstop%20offset='1'%20stop-color='%235e4be0'/%3E%3C/linearGradient%3E%3C/defs%3E%3Crect%20width='40'%20height='40'%20fill='url(%23g)'/%3E%3C/svg%3E";

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section style={{ marginBottom: "1.75rem" }}>
      <h3
        className="data"
        style={{
          fontSize: "0.72rem",
          letterSpacing: "0.08em",
          textTransform: "uppercase",
          color: "var(--text-faint)",
          margin: "0 0 0.6rem",
        }}
      >
        {title}
      </h3>
      <div style={{ display: "flex", flexWrap: "wrap", gap: "0.75rem", alignItems: "center" }}>
        {children}
      </div>
    </section>
  );
}

export function Gallery() {
  const [runId, setRunId] = React.useState(0);
  const [threshold, setThreshold] = React.useState<number[]>([60]);
  const [progress, setProgress] = React.useState(8);
  const [toastOpen, setToastOpen] = React.useState(false);

  React.useEffect(() => {
    const t = setTimeout(() => setProgress(72), 250);
    return () => clearTimeout(t);
  }, []);

  return (
    <div>
      <Section title="Buttons">
        <Button>Analyze posting</Button>
        <Button variant="ghost">Cancel</Button>
        <Button size="sm">Small</Button>
        <Button disabled>Disabled</Button>
      </Section>

      <Section title="Signal badges">
        <span className="ss-badge ss-badge-danger">Likely scam</span>
        <span className="ss-badge ss-badge-caution">Uncertain</span>
        <span className="ss-badge ss-badge-verified">Looks legitimate</span>
      </Section>

      <section style={{ marginBottom: "1.75rem" }}>
        <h3
          className="data"
          style={{
            fontSize: "0.72rem",
            letterSpacing: "0.08em",
            textTransform: "uppercase",
            color: "var(--text-faint)",
            margin: "0 0 0.6rem",
          }}
        >
          Glass card + UV sweep
        </h3>
        <GlassCard>
          <UvSweep runId={runId} />
          <p className="font-display" style={{ margin: 0, fontWeight: 600, fontSize: "1.05rem" }}>
            Suspicious posting
          </p>
          <p className="text-muted" style={{ margin: "0.4rem 0 0", fontSize: "0.9rem", lineHeight: 1.55 }}>
            URGENT hiring! Work from home and earn <span className="data">$5,000</span>/week, no
            experience needed. Send a <span className="data">$200</span> processing fee to activate
            your account.
          </p>
          <div style={{ marginTop: "1rem" }}>
            <Button size="sm" onClick={() => setRunId((n) => n + 1)}>
              Run inspection
            </Button>
          </div>
        </GlassCard>
      </section>

      <Section title="Tabs">
        <Tabs defaultValue="verdict" style={{ width: "100%" }}>
          <TabsList>
            <TabsTrigger value="verdict">Verdict</TabsTrigger>
            <TabsTrigger value="flags">Flags</TabsTrigger>
            <TabsTrigger value="similar">Similar</TabsTrigger>
          </TabsList>
          <TabsContent value="verdict" className="text-muted" style={{ marginTop: "0.75rem", fontSize: "0.9rem" }}>
            The calibrated probability and its label.
          </TabsContent>
          <TabsContent value="flags" className="text-muted" style={{ marginTop: "0.75rem", fontSize: "0.9rem" }}>
            Ranked feature contributions, in log-odds.
          </TabsContent>
          <TabsContent value="similar" className="text-muted" style={{ marginTop: "0.75rem", fontSize: "0.9rem" }}>
            The three nearest confirmed scams.
          </TabsContent>
        </Tabs>
      </Section>

      <Section title="Switch">
        <Switch defaultChecked aria-label="Example switch on" />
        <Switch aria-label="Example switch off" />
      </Section>

      <Section title="Slider (threshold)">
        <div style={{ width: "220px" }}>
          <Slider value={threshold} onValueChange={setThreshold} min={0} max={100} step={1} />
        </div>
        <span className="data">{threshold[0]}%</span>
      </Section>

      <Section title="Progress">
        <div style={{ width: "220px" }}>
          <Progress value={progress} />
        </div>
        <span className="data">{progress}%</span>
      </Section>

      <Section title="Toggle group">
        <ToggleGroup type="single" defaultValue="30d" aria-label="Trend window">
          <ToggleGroupItem value="7d">7d</ToggleGroupItem>
          <ToggleGroupItem value="30d">30d</ToggleGroupItem>
          <ToggleGroupItem value="90d">90d</ToggleGroupItem>
        </ToggleGroup>
      </Section>

      <Section title="Accordion">
        <Accordion type="single" collapsible style={{ width: "100%", maxWidth: "440px" }}>
          <AccordionItem value="a">
            <AccordionTrigger>Why linear, not XGBoost?</AccordionTrigger>
            <AccordionContent>
              A linear model&apos;s contribution is exactly coefficient x tf-idf — true, and
              computable in Java in microseconds. That is what the UI shows.
            </AccordionContent>
          </AccordionItem>
          <AccordionItem value="b">
            <AccordionTrigger>What is the majority-class baseline?</AccordionTrigger>
            <AccordionContent>
              Predicting &quot;legitimate&quot; for every posting scores ~95% accuracy and catches
              zero scams. That is why accuracy is never the headline.
            </AccordionContent>
          </AccordionItem>
        </Accordion>
      </Section>

      <Section title="Separator">
        <div style={{ display: "flex", alignItems: "center", gap: "0.5rem", fontSize: "0.9rem" }}>
          <span>Verdict</span>
          <Separator orientation="vertical" style={{ height: "1rem" }} />
          <span>Flags</span>
          <Separator orientation="vertical" style={{ height: "1rem" }} />
          <span>Similar</span>
        </div>
      </Section>

      <Section title="Avatar">
        <Avatar>
          <AvatarImage src={AVATAR_SRC} alt="" />
          <AvatarFallback>SS</AvatarFallback>
        </Avatar>
        <Avatar>
          <AvatarFallback>MJ</AvatarFallback>
        </Avatar>
      </Section>

      <Section title="Scroll area (flat — never glass on a scrolling list)">
        <ScrollArea style={{ height: "140px", width: "260px" }}>
          <div style={{ padding: "0.5rem 0.75rem" }}>
            {Array.from({ length: 12 }).map((_, i) => (
              <div
                key={i}
                style={{
                  padding: "0.5rem 0.25rem",
                  borderBottom: "1px solid var(--border)",
                  fontSize: "0.85rem",
                }}
              >
                <span className="data">#{i + 1}</span> similar confirmed scam
              </div>
            ))}
          </div>
        </ScrollArea>
      </Section>

      <Section title="Dropdown menu">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="sm">
              Actions
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent>
            <DropdownMenuLabel>Verdict</DropdownMenuLabel>
            <DropdownMenuItem>Copy permalink</DropdownMenuItem>
            <DropdownMenuItem>Report as wrong</DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem>Export JSON</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </Section>

      <Section title="Popover">
        <Popover>
          <PopoverTrigger asChild>
            <Button variant="ghost" size="sm">
              Contribution
            </Button>
          </PopoverTrigger>
          <PopoverContent>
            <p style={{ margin: 0, fontWeight: 600 }}>processing fee</p>
            <p className="text-muted" style={{ margin: "0.4rem 0 0", fontSize: "0.85rem", lineHeight: 1.5 }}>
              This phrase added <span className="data">+0.41</span> to the log-odds of scam.
            </p>
          </PopoverContent>
        </Popover>
      </Section>

      <Section title="Tooltip">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant="ghost" size="sm">
              PR-AUC
            </Button>
          </TooltipTrigger>
          <TooltipContent>
            Precision-recall AUC — the primary metric on a 4.8% positive class.
          </TooltipContent>
        </Tooltip>
      </Section>

      <Section title="Dialog (glass)">
        <Dialog>
          <DialogTrigger asChild>
            <Button size="sm">Open dialog</Button>
          </DialogTrigger>
          <DialogContent>
            <DialogTitle>Report this verdict</DialogTitle>
            <DialogDescription>
              Tell us what we got wrong. A label only changes on agreement between two independent
              reporters, or a moderator&apos;s decision.
            </DialogDescription>
            <div style={{ marginTop: "1.25rem", display: "flex", gap: "0.5rem", justifyContent: "flex-end" }}>
              <DialogClose asChild>
                <Button variant="ghost" size="sm">
                  Cancel
                </Button>
              </DialogClose>
              <DialogClose asChild>
                <Button size="sm">Submit report</Button>
              </DialogClose>
            </div>
          </DialogContent>
        </Dialog>
      </Section>

      <Section title="Toast">
        <Button
          size="sm"
          onClick={() => {
            setToastOpen(false);
            requestAnimationFrame(() => setToastOpen(true));
          }}
        >
          Show toast
        </Button>
        <Toast open={toastOpen} onOpenChange={setToastOpen} duration={4000}>
          <ToastTitle>Verdict saved</ToastTitle>
          <ToastDescription>Your analysis permalink is ready to share.</ToastDescription>
          <ToastClose asChild>
            <Button variant="ghost" size="sm" aria-label="Dismiss">
              Close
            </Button>
          </ToastClose>
        </Toast>
      </Section>
    </div>
  );
}
