; Owned witness instructions. All labels and byte sequences belong to shared.h ABI v1.
option casemap:none

.code
PUBLIC C48Wrapper, C48WrapperBegin, C48Arm, C48Call, C48AfterCall, C48WrapperEnd
PUBLIC C48ReturnLeaf, C48ReturnLeafEnd, C48YmmLeaf, C48YmmLeafEnd
PUBLIC C48FaultLeaf, C48FaultLeafEnd, C48ReadXcr0, C48XcrEntry, C48XcrEnd

C48Wrapper PROC FRAME
C48WrapperBegin LABEL BYTE
    db 048h,083h,0ech,028h
    .allocstack 028h
    .endprolog
    db 049h,089h,0cbh,048h,089h,0d1h,04ch,089h,0c2h
    db 04dh,089h,0c8h
C48Arm LABEL BYTE
    db 0cch
C48Call LABEL BYTE
    db 041h,0ffh,0d3h
C48AfterCall LABEL BYTE
    db 048h,083h,0c4h,028h,0c3h
C48WrapperEnd LABEL BYTE
C48Wrapper ENDP

C48ReturnLeaf LABEL BYTE
    db 048h,0b8h,0efh,0cdh,0abh,089h,067h,045h,023h,0f1h,0c3h
C48ReturnLeafEnd LABEL BYTE
C48YmmLeaf LABEL BYTE
    db 0c5h,0feh,06fh,001h,0c5h,0feh,07fh,002h
    db 0c4h,0e3h,07dh,018h,0c0h,001h,0c4h,0c1h,07eh,07fh,000h
    db 0c5h,0f8h,077h,0c3h
C48YmmLeafEnd LABEL BYTE
C48FaultLeaf LABEL BYTE
    db 00fh,00bh,0c3h
C48FaultLeafEnd LABEL BYTE
C48ReadXcr0 LABEL BYTE
C48XcrEntry LABEL BYTE
    db 031h,0c9h,00fh,001h,0d0h,048h,0c1h,0e2h,020h,048h,009h,0d0h,0c3h
C48XcrEnd LABEL BYTE
END
