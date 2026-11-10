option casemap:none
.code
PUBLIC C48FullWrapper, C48FullWrapperBegin, C48FullArm, C48FullCall, C48FullAfterCall, C48FullWrapperEnd
PUBLIC C48FullReadXcr0, C48FullXcrEntry, C48FullXcrEnd
C48FullWrapper PROC FRAME
C48FullWrapperBegin LABEL BYTE
    db 048h,083h,0ech,028h
    .allocstack 028h
    .endprolog
    db 049h,089h,0cbh,048h,089h,0d1h,031h,0d2h,041h,0b8h,030h,000h,000h,000h
C48FullArm LABEL BYTE
    db 0cch
C48FullCall LABEL BYTE
    db 041h,0ffh,0d3h
C48FullAfterCall LABEL BYTE
    db 048h,083h,0c4h,028h,0c3h
C48FullWrapperEnd LABEL BYTE
C48FullWrapper ENDP
C48FullReadXcr0 LABEL BYTE
C48FullXcrEntry LABEL BYTE
    db 031h,0c9h,00fh,001h,0d0h,048h,0c1h,0e2h,020h,048h,009h,0d0h,0c3h
C48FullXcrEnd LABEL BYTE
END
