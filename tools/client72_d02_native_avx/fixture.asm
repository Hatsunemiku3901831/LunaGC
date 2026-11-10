option casemap:none
.code
PUBLIC Probe, ProbeEntry, ProbeSite, ProbeReturn, ProbeEnd
PUBLIC ReadXcr0, Xcr0Entry, Xcr0Site, Xcr0Return, Xcr0End
PUBLIC Witness, WitnessEntry, WitnessSite, WitnessEnd
Probe PROC
ProbeEntry LABEL BYTE
    db 0c5h,0feh,06fh,001h
    db 0c5h,0feh,07fh,002h
ProbeSite LABEL BYTE
    db 0c4h,0e3h,07dh,018h,0c0h,001h
    db 0c4h,0c1h,07eh,07fh,000h
    db 0c5h,0f8h,077h
ProbeReturn LABEL BYTE
    db 0c3h
ProbeEnd LABEL BYTE
Probe ENDP
ReadXcr0 PROC
Xcr0Entry LABEL BYTE
    db 031h,0c9h
Xcr0Site LABEL BYTE
    db 00fh,001h,0d0h
    db 048h,0c1h,0e2h,020h,048h,009h,0d0h
Xcr0Return LABEL BYTE
    db 0c3h
Xcr0End LABEL BYTE
ReadXcr0 ENDP
Witness PROC
WitnessEntry LABEL BYTE
WitnessSite LABEL BYTE
    db 00fh,00bh
    db 0c3h
WitnessEnd LABEL BYTE
Witness ENDP
END
