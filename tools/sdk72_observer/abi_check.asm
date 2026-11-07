EXTERN fixture_run:PROC
.code
abi_check PROC
 push rbx
 push rbp
 push rsi
 push rdi
 push r12
 push r13
 push r14
 push r15
 sub rsp, 40
 mov rbx, 101h
 mov rbp, 102h
 mov rsi, 103h
 mov rdi, 104h
 mov r12, 105h
 mov r13, 106h
 mov r14, 107h
 mov r15, 108h
 call fixture_run
 test eax, eax
 jz fail
 cmp rbx, 101h
 jne fail
 cmp rbp, 102h
 jne fail
 cmp rsi, 103h
 jne fail
 cmp rdi, 104h
 jne fail
 cmp r12, 105h
 jne fail
 cmp r13, 106h
 jne fail
 cmp r14, 107h
 jne fail
 cmp r15, 108h
 jne fail
 mov eax, 1
 jmp done
fail:
 xor eax, eax
done:
 add rsp, 40
 pop r15
 pop r14
 pop r13
 pop r12
 pop rdi
 pop rsi
 pop rbp
 pop rbx
 ret
abi_check ENDP
END
