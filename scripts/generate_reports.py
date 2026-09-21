#!/usr/bin/env python3
"""Render milestone and complete-guide PDFs from saved facts and file inventory.
Requires reportlab. Run from any directory; outputs stay in milestone-reports.
"""
from pathlib import Path
import json, re, html
from reportlab.pdfgen import canvas
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle, Preformatted, KeepTogether, CondPageBreak
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

ROOT=Path(__file__).resolve().parent.parent
OUT=ROOT/'milestone-reports'
FONT=Path('/usr/share/fonts/truetype/dejavu')
if (FONT/'DejaVuSans.ttf').exists():
    pdfmetrics.registerFont(TTFont('Body',str(FONT/'DejaVuSans.ttf')))
    pdfmetrics.registerFont(TTFont('BodyBold',str(FONT/'DejaVuSans-Bold.ttf')))
    pdfmetrics.registerFontFamily('Body',normal='Body',bold='BodyBold',italic='Body',boldItalic='BodyBold')
    pdfmetrics.registerFont(TTFont('Code',str(FONT/'DejaVuSansMono.ttf')))
else:
    pdfmetrics.registerFontFamily('Body',normal='Helvetica',bold='Helvetica-Bold',italic='Helvetica-Oblique',boldItalic='Helvetica-BoldOblique')
BODY='Body' if 'Body' in pdfmetrics.getRegisteredFontNames() else 'Helvetica'
BOLD='BodyBold' if 'BodyBold' in pdfmetrics.getRegisteredFontNames() else 'Helvetica-Bold'
CODE='Code' if 'Code' in pdfmetrics.getRegisteredFontNames() else 'Courier'
INK=colors.HexColor('#18222c');MUTED=colors.HexColor('#56616a');ACCENT=colors.HexColor('#28634f');LINE=colors.HexColor('#d9e0dd')
ST=getSampleStyleSheet()
ST.add(ParagraphStyle(name='DoctorBody',fontName=BODY,fontSize=9,leading=14,textColor=INK,spaceAfter=8,splitLongWords=True))
ST.add(ParagraphStyle(name='DoctorSmall',parent=ST['DoctorBody'],fontSize=7.7,leading=11,textColor=MUTED))
ST.add(ParagraphStyle(name='DoctorTitle',fontName=BOLD,fontSize=29,leading=34,textColor=INK,spaceAfter=18))
ST.add(ParagraphStyle(name='DoctorH1',fontName=BOLD,fontSize=18,leading=23,textColor=INK,spaceBefore=14,spaceAfter=11,keepWithNext=True))
ST.add(ParagraphStyle(name='DoctorH2',fontName=BOLD,fontSize=11,leading=16,textColor=ACCENT,spaceBefore=13,spaceAfter=7,keepWithNext=True))
ST.add(ParagraphStyle(name='DoctorCode',fontName=CODE,fontSize=6.9,leading=10,textColor=INK,spaceAfter=7,splitLongWords=True))

def clean(value):
    return str(value).replace('\u2011','-').replace('\u2013','-').replace('\u2014','-').replace('→','->').replace('↗','').replace('\u00a0',' ')

def p(value,style='DoctorBody'):
    return Paragraph(html.escape(clean(value)),ST[style])

def rich(value):
    value=html.escape(clean(value))
    value=re.sub(r'\[([^]]+)\]\(([^)]+)\)',r'\1 (\2)',value)
    value=re.sub(r'\*\*([^*]+)\*\*',r'<b>\1</b>',value)
    value=re.sub(r'`([^`]+)`',r'<font name="'+CODE+r'">\1</font>',value)
    return Paragraph(value,ST['DoctorBody'])

class Pages(canvas.Canvas):
    def __init__(self,*args,**kwargs):
        super().__init__(*args,**kwargs);self.states=[]
    def showPage(self):
        self.states.append(dict(self.__dict__));self._startPage()
    def save(self):
        total=len(self.states)
        for state in self.states:
            self.__dict__.update(state);self.footer(total);super().showPage()
        super().save()
    def footer(self,total):
        self.setStrokeColor(LINE);self.line(48,798,547,798);self.line(48,43,547,43)
        self.setFont(BOLD,8);self.setFillColor(INK);self.drawString(48,810,'CODEBASE DOCTOR')
        self.setFont(BODY,7);self.setFillColor(MUTED);self.drawRightString(547,810,'ENGINEERING RECORD / 20 SEPTEMBER 2026')
        self.drawString(48,29,'Local MVP - implementation and runtime evidence are recorded separately')
        self.drawRightString(547,29,f'{self._pageNumber} / {total}')

def build(name,story):
    OUT.mkdir(exist_ok=True)
    SimpleDocTemplate(str(OUT/name),pagesize=(595.28,841.89),leftMargin=48,rightMargin=48,topMargin=60,bottomMargin=58,title=name.replace('.pdf','').replace('-',' '),author='Codebase Doctor project documentation').build(story,canvasmaker=Pages)
    print(name)

def tree(rows):
    root={}
    for row in rows:
        node=root
        for part in row['path'].split('/'):node=node.setdefault(part,{})
    lines=[]
    def visit(node,depth=0):
        for key,child in sorted(node.items()):
            lines.append('  '*depth+key+('/' if child else ''));visit(child,depth+1)
    visit(root)
    return [Preformatted('\n'.join(lines),ST['DoctorCode'])]

def inventory(rows):
    result=[]
    for row in rows:
        result.append(KeepTogether([p(row['path'],'DoctorCode'),p(row['change']+' | Delivery stage '+str(row['milestone']),'DoctorSmall'),p(row['purpose']),Spacer(1,5)]))
    return result

def markdown(path):
    story=[];buffer=[];code=False
    def flush():
        if buffer:story.append(rich(' '.join(buffer)));buffer.clear()
    for line in path.read_text().splitlines():
        if line.startswith('```'):
            flush();code=not code;continue
        if code:
            story.append(p(line or ' ','DoctorCode'));continue
        if not line.strip():flush();continue
        if line.startswith('#'):
            flush();level=len(line)-len(line.lstrip('#'));story.append(p(line.lstrip('#').strip(),'DoctorH1' if level==1 else 'DoctorH2'));continue
        if line.startswith('|'):
            flush()
            if set(line.replace('|','').replace(' ','').replace(':',''))<=set('-'):continue
            story.append(p(' | '.join(t.strip() for t in line.strip('|').split('|')),'DoctorSmall'));continue
        if re.match(r'^(- |[0-9]+\. )',line):
            flush();story.append(rich(line));continue
        buffer.append(line)
    flush();return story

def main():
    milestones=json.loads((ROOT/'docs/milestone-data.json').read_text())
    files=json.loads((ROOT/'docs/file-inventory.json').read_text())
    for stage in milestones[1:]:
        number=stage['number'];rows=[x for x in files if x['milestone']==number]
        story=[Spacer(1,24),p(f'MILESTONE {number:02} / DELIVERY RECORD','DoctorSmall'),p(stage['title'],'DoctorTitle'),p(stage['status'],'DoctorH2'),p(stage['delivered']),Spacer(1,18),p('What was built','DoctorH2'),p(stage['delivered']),p('Debugging and evidence','DoctorH2'),p(stage['checks']),p('Remaining limitations','DoctorH2'),p(stage['limits']),Spacer(1,16),p('Scope of this document','DoctorH2'),p(f'The following pages list the {len(rows)} source, configuration, test and documentation files assigned to this stage, with their purpose. Shared files are assigned to their principal delivery stage; the complete guide lists every authored file. Generated dependencies, caches, private settings and saved repository data are excluded.'),p('Owner workflow','DoctorH2'),p('All development stages were authorized together. In-product approval is still required before repairs and again before publishing. No remote GitHub changes were made during development verification.'),PageBreak(),p('Folder structure','DoctorH1')]
        story+=tree(rows)+[PageBreak(),p('Files and responsibilities','DoctorH1')]+inventory(rows)
        build(f'Milestone-{number:02}-{stage["title"].replace(",","").replace(" ","-")}.pdf',story)
    story=[Spacer(1,35),p('PROJECT HANDBOOK / LOCAL MVP','DoctorSmall'),p('Codebase Doctor\nComplete guide','DoctorTitle'),p('Repository understanding. Reviewed repairs. Evidence you can inspect.'),Spacer(1,20),p('Delivery and acceptance','DoctorH2'),p('The local implementation, portfolio-inspired UI and documentation are delivered. Static scanning and local checks passed. Real Docker execution, live Groq repairs and GitHub publication remain unverified until the required environment and credentials are available.'),p('Inside this guide','DoctorH2')]
    for section in ['Getting started and operation','Security and data boundaries','Architecture and API','Milestones and validation','Report contract','Complete folder tree and per-file responsibilities']:
        story.append(p('- '+section))
    story.append(PageBreak())
    for name in ['README.md','docs/USER_GUIDE.md','docs/SECURITY.md','docs/ARCHITECTURE.md','docs/API.md','docs/MILESTONES.md','docs/VALIDATION.md','docs/report-format.md']:
        story.append(CondPageBreak(230));story+=markdown(ROOT/name)
    story+=[PageBreak(),p('Complete authored-file tree','DoctorH1'),p('Generated runtime files, private configuration values, PDF outputs and dependencies are excluded. The historical Milestone 1 PDF preserves its original delivery inventory.')]+tree(files)
    story+=[PageBreak(),p('Every file and its purpose','DoctorH1')]+inventory(files)
    build('Codebase-Doctor-Complete-Guide.pdf',story)

if __name__=='__main__':main()
