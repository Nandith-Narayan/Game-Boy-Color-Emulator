import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.text.NumberFormat;
import java.util.Locale;

public class CPU {
    int pc;
    int sp;
    //B, C, D, E, H, L, F, A
    final int B=0,C=1,D=2,E=3,H=4,L=5,F=6,A=7;
    byte reg[] = new byte[8];
    public boolean interruptsEnabled = false;
    private boolean enableInterruptsNextInst = false;
    public boolean halted=false;
    PrintWriter pw;
    PPU ppu;

    public CPU(){
        Memory.initMemory();
        ppu = new PPU();
        APU.initiateAPU();
        try {
            pw = new PrintWriter("log.txt");

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        /*reg[C]=19;
        reg[A]=1;
        reg[F]=0xB;
        reg[E]=(byte)0xD8;
        reg[H]=(byte)0x01;
        reg[L]=(byte)0x4D;*/
        reg[A] = 0x011; // To enter GBC mode, register A must be initialized to 0x11
        pc = 256;
        sp = 65534;
        //sp = 0;
        long bt = System.nanoTime();
        long n = 500_000_000;
        for(int i=0;i<n;i++) {
            int cyclesPassed = run();
            updateTimer(cyclesPassed);
            ppu.processCycles(cyclesPassed);
            APU.processCycles(cyclesPassed);
        }
        long at = System.nanoTime();
        System.out.println(NumberFormat.getInstance().format(at-bt));
        System.out.println((at-bt)/(n*1.0));
        pw.close();
    }

    public int run(){
        int cycles = 0;
        int address;
        int oldPC = pc;
        Memory.pc = pc;
        byte val;
        short temp,temp2;
        byte carry;
        //logStatus();

        // Check for interrupts
        if(interruptsEnabled){
            byte IE = Memory.read(0x0FFFF);
            byte IF = Memory.read(0x0FF0F);

            // Check for V-Blank Interrupt
            if((IE&0x01) != 0 && (IF&0x01) != 0){
                halted = false;
                IF &= 0x0FE;
                interruptsEnabled = false;
                Memory.write(sp - 1, (byte) (((pc) >>> 8) & 0x000000FF));
                Memory.write(sp - 2, (byte) ((pc) & 0x000000FF));
                sp -= 2;
                pc = 0x040;
                // System.out.println("V");
            // Check for LCD STAT Interrupt
            }else if((IE&0x02) != 0 && (IF&0x02) != 0){
                //System.out.println("Handling STAT Interrupt");
                halted = false;
                IF &= 0x0FD;
                interruptsEnabled = false;
                Memory.write(sp - 1, (byte) (((pc) >>> 8) & 0x000000FF));
                Memory.write(sp - 2, (byte) ((pc) & 0x000000FF));
                sp -= 2;
                pc = 0x048;

                // Check for Timer Interrupt
            }else if((IE&0x04) != 0 && (IF&0x04) != 0){
                //System.out.println("T");
                halted = false;
                IF &= 0x0FB;
                interruptsEnabled = false;
                Memory.write(sp - 1, (byte) (((pc) >>> 8) & 0x000000FF));
                Memory.write(sp - 2, (byte) ((pc) & 0x000000FF));
                sp -= 2;
                pc = 0x050;
                // Check for Serial Interrupt
            }else if((IE&0x08) != 0 && (IF&0x08) != 0){
                halted = false;
                IF &= 0x0F7;
                interruptsEnabled = false;
                Memory.write(sp - 1, (byte) (((pc) >>> 8) & 0x000000FF));
                Memory.write(sp - 2, (byte) ((pc) & 0x000000FF));
                sp -= 2;
                pc = 0x058;
                // Check for Joypad Interrupt
            }else if((IE&0x10) != 0 && (IF&0x10) != 0){
                halted = false;
                IF &= 0x0EF;
                interruptsEnabled = false;
                Memory.write(sp - 1, (byte) (((pc) >>> 8) & 0x000000FF));
                Memory.write(sp - 2, (byte) ((pc) & 0x000000FF));
                sp -= 2;
                pc = 0x060;
            }
            Memory.write(0x0FF0F, IF);
        }

        if(enableInterruptsNextInst){
            interruptsEnabled = true;
            enableInterruptsNextInst = false;
        }

        if(halted){
            return 4;
        }

        byte opcode = Memory.read(pc);
        switch(opcode){
            case 0: // 00 NOP ;
                cycles += 4;
                pc += 1;
                break;
            case 1: // 01 LD BC, d16
                reg[B] = Memory.read(pc+2);
                reg[C] = Memory.read(pc+1);
                pc += 3;
                cycles += 12;
                break;
            case 2: // 02 LD (BC), A
                address = ((reg[B] << 8) | (reg[C] & 0xFF))& 0x0000FFFF;
                Memory.write(address, reg[A]);
                pc += 1;
                cycles += 8;
                break;
            case 3: // 03 INC BC
                // No need to set the Carry Flag
                if(reg[C] == -1)
                        reg[B] += 1;
                reg[C] += 1;
                pc += 1;
                cycles += 8;
                break;
            case 4: // 04 INC B
                reg[B] += 1;
                // set Zero Flag
                if(reg[B] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                // set Subtract Flag
                resetSubtractFlag();
                // set Half Carry Flag
                if((reg[B] & 0x0000000F) == 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case 5: // 05 DEC B
                reg[B] -= 1;
                // set Subtract Flag
                setSubtractFlag();
                // set Half Carry Flag
                if((reg[B] & 0x0000000F) == 0x0000000F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();


                // set Zero Flag
                if(reg[B] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case 6: // 06 LD B, d8
                reg[B] = Memory.read(pc+1);
                pc += 2;
                cycles += 8;
                break;
            case 7: // 07 RLCA
                if((reg[A] & 0x00000080) != 0){
                    setCarryFlag();
                    reg[A] <<= 1;
                    reg[A] |= 0x00000001;
                }else{
                    resetCarryFlag();
                    reg[A] <<= 1;
                    reg[A] &= 0x000000FE;
                }

                resetZeroFlag();
                resetSubtractFlag();
                resetHalfCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case 8: // 08 LD (a16), SP
                address = ((Memory.read(pc+2) << 8) | (Memory.read(pc+1) & 0xFF)) & 0x0000FFFF;
                Memory.write(address, (byte)sp);
                Memory.write(address+1, (byte)(sp >>> 8));
                pc += 3;
                cycles += 20;
                break;
            case 9: // 09 ADD HL, BC
                temp = (short) (((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF);
                temp2 = (short) (((reg[B] << 8) | (reg[C] & 0xFF))& 0x0000FFFF);

                resetSubtractFlag();
                // set Half Carry Flag
                if((((temp & 0xFFF) + (temp2&0xFFF))&0x1000) != 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if((((temp & 0xFFFF) + (temp2&0xFFFF))&0x10000) != 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                temp += temp2;

                reg[H] = (byte) ((temp >>> 8) & 0x000000FF);
                reg[L] = (byte) (temp & 0x000000FF);
                pc += 1;
                cycles += 8;
                break;
            case 10: // 0A LD A, (BC)
                address = ((reg[B] << 8) | (reg[C] & 0xFF))& 0x0000FFFF;
                reg[A] = Memory.read(address);
                pc += 1;
                cycles += 8;
                break;
            case 11: // 0B DEC BC
                if(reg[C] == 0)
                    reg[B] -= 1;
                reg[C] -= 1;
                pc += 1;
                cycles += 8;
                break;
            case 12: // 0C INC C
                reg[C] += 1;
                // set Zero Flag
                if(reg[C] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                // set Subtract Flag
               resetSubtractFlag();
                // set Half Carry Flag
                if((reg[C] & 0x0000000F) == 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case 13: // 0D DEC C
                reg[C] -= 1;
                // set Subtract Flag
                setSubtractFlag();
                // set Half Carry Flag
                if((reg[C] & 0x0000000F) == 0x0000000F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();


                // set Zero Flag
                if(reg[C] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                pc += 1;
                cycles += 4;
                break;
            case 14: // 0E LD C, d8
                reg[C] = Memory.read(pc+1);
                pc += 2;
                cycles += 8;
                break;
            case 15: // 0F RRCA
                if((reg[A] & 0x00000001) != 0){
                    setCarryFlag();
                    reg[A] >>>= 1;
                    reg[A] |= 0x00000080;
                }else{
                    resetCarryFlag();
                    reg[A] >>>= 1;
                    reg[A] &= 0x0000007F;
                }

                resetZeroFlag();
                resetSubtractFlag();
                resetHalfCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case 17: // 11 LD DE, d16
                reg[D] = Memory.read(pc+2);
                reg[E] = Memory.read(pc+1);
                pc += 3;
                cycles += 12;
                break;
            case 18: // 12 LD (DE), A
                address = ((reg[D] << 8) | (reg[E] & 0xFF))& 0x0000FFFF;
                Memory.write(address, reg[A]);
                pc += 1;
                cycles += 8;
                break;
            case 19: // 13 INC DE
                // No need to set the Carry Flag
                if(reg[E] == -1)
                    reg[D] += 1;
                reg[E] += 1;
                pc += 1;
                cycles += 8;
                break;
            case 20: // 14 INC D
                reg[D] += 1;
                // set Zero Flag
                if(reg[D] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                // set Subtract Flag
               resetSubtractFlag();
                // set Half Carry Flag
                if((reg[D] & 0x0000000F) == 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case 21: // 15 DEC D
                reg[D] -= 1;
                // set Subtract Flag
                setSubtractFlag();
                // set Half Carry Flag
                if((reg[D] & 0x0000000F) == 0x0000000F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();


                // set Zero Flag
                if(reg[D] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case 22: // 16 LD D, d8
                reg[D] = Memory.read(pc+1);
                pc += 2;
                cycles += 8;
                break;
            case 23: // 17 RlA
                val = (byte) (reg[F] & 0x00000001);
                if((reg[A] & 0x00000080) != 0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                reg[A] <<= 1;
                reg[A] &= 0xFE;
                reg[A] |= val;


                resetZeroFlag();
                resetSubtractFlag();
                resetHalfCarryFlag();

                pc += 1;
                cycles += 4;
                break;
            case 24: // 18 JR r8
                pc += (byte)Memory.read(pc + 1);
                pc += 2;
                cycles += 12;
                break;
            case 25: // 19 ADD HL, DE
                temp = (short) (((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF);
                temp2 = (short) (((reg[D] << 8) | (reg[E] & 0xFF))& 0x0000FFFF);

                resetSubtractFlag();
                // set Half Carry Flag
                if((((temp & 0xFFF) + (temp2&0xFFF))&0x1000) != 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if((((temp & 0xFFFF) + (temp2&0xFFFF))&0x10000) != 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                temp += temp2;

                reg[H] = (byte) ((temp >>> 8) & 0x000000FF);
                reg[L] = (byte) (temp & 0x000000FF);
                pc += 1;
                cycles += 8;
                break;
            case 26: // 1A LD A, (DE)
                address = ((reg[D] << 8) | (reg[E] & 0xFF))& 0x0000FFFF;
                reg[A] = Memory.read(address);
                pc += 1;
                cycles += 8;
                break;
            case 27: // 1B DEC DE
                if(reg[E] == 0)
                    reg[D] -= 1;
                reg[E] -= 1;
                pc += 1;
                cycles += 8;
                break;
            case 28: // 1C INC E
                reg[E] += 1;
                // set Zero Flag
                if(reg[E] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                // set Subtract Flag
               resetSubtractFlag();
                // set Half Carry Flag
                if((reg[E] & 0x0000000F) == 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case 29: // 1D DEC E
                reg[E] -= 1;
                // set Subtract Flag
                setSubtractFlag();
                // set Half Carry Flag
                if((reg[E] & 0x0000000F) == 0x0000000F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();


                // set Zero Flag
                if(reg[E] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case 30: // 1E LD E, d8
                reg[E] = Memory.read(pc+1);
                pc += 2;
                cycles += 8;
                break;
            case 31: // 1F RRA
                val = (byte) (reg[F] & 0x00000001);
                if((reg[A] & 0x00000001) != 0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                reg[A] >>>= 1;
                reg[A] &= 0x7F;
                reg[A] |= val << 7;


                resetZeroFlag();
                resetSubtractFlag();
                resetHalfCarryFlag();

                pc += 1;
                cycles += 4;
                break;
            case 32: // 20 JR NZ, r8
                if((reg[F] & 0x00000008) == 0) {
                    pc += (byte)Memory.read(pc + 1);
                    cycles += 4;
                }
                pc += 2;
                cycles += 8;
                break;
            case 33: // 21 LD HL, d16
                reg[H] = Memory.read(pc+2);
                reg[L] = Memory.read(pc+1);
                pc += 3;
                cycles += 12;
                break;
            case 34: // 22 LD (HL+), A
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                Memory.write(address, reg[A]);
                // No need to set the Carry Flag
                if(reg[L] == -1)
                    reg[H] += 1;
                reg[L] += 1;
                pc += 1;
                cycles += 8;
                break;
            case 35: // 23 INC HL
                // No need to set the Carry Flag
                if(reg[L] == -1)
                    reg[H] += 1;
                reg[L] += 1;
                pc += 1;
                cycles += 8;
                break;
            case 36: // 24 INC H
                reg[H] += 1;
                // set Zero Flag
                if(reg[H] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                // set Subtract Flag
                resetSubtractFlag();
                // set Half Carry Flag
                if((reg[H] & 0x0000000F) == 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();

                pc += 1;
                cycles += 4;
                break;
            case 37: // 25 DEC H
                reg[H] -= 1;
                // set Subtract Flag
                setSubtractFlag();
                // set Half Carry Flag
                if((reg[H] & 0x0000000F) == 0x0000000F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();


                // set Zero Flag
                if(reg[H] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case 38: // 26 LD H, d8
                reg[H] = Memory.read(pc+1);
                pc += 2;
                cycles += 8;
                break;
            case 39:// 27 DAA
                if ((reg[F]&0x00000004) == 0) {
                    if ((reg[F]&0x00000001) != 0 || (reg[A]&0x000000FF) > 0x099) { reg[A] += 0x060; setCarryFlag(); }
                    if ((reg[F]&0x00000002) != 0 || (reg[A]&0x0000000F) > 0x09) { reg[A] += 0x06; }
                } else {  // after a subtraction, only adjust if (half-)carry occurred
                    if ((reg[F]&0x00000001) != 0) { reg[A] -= 0x060; }
                    if ((reg[F]&0x00000002) != 0) { reg[A] -= 0x06; }
                }
                if(reg[A]==0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                resetHalfCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case 40: // 28 JR Z, r8
                if((reg[F] & 0x00000008) != 0) {
                    pc += (byte)Memory.read(pc + 1);
                    cycles += 4;
                }
                pc += 2;
                cycles += 8;
                break;
            case 41: // 29 ADD HL, HL
                temp = (short) (((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF);

                resetSubtractFlag();
                // set Half Carry Flag
                if((((temp & 0xFFF) + (temp&0xFFF))&0x1000) != 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if((((temp & 0xFFFF) + (temp&0xFFFF))&0x10000) != 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                temp += temp;

                reg[H] = (byte) ((temp >>> 8) & 0x000000FF);
                reg[L] = (byte) (temp & 0x000000FF);
                pc += 1;
                cycles += 8;
                break;
            case 42: // 2A LD A, (HL+)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                reg[A] = Memory.read(address);
                // No need to set the Carry Flag
                if(reg[L] == -1)
                    reg[H] += 1;
                reg[L] += 1;
                pc += 1;
                cycles += 8;
                break;
            case 43: // 2B DEC HL
                if(reg[L] == 0)
                    reg[H] -= 1;
                reg[L] -= 1;
                pc += 1;
                cycles += 8;
                break;
            case 44: // 2C INC L
                reg[L] += 1;
                // set Zero Flag
                if(reg[L] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                // set Subtract Flag
               resetSubtractFlag();
                // set Half Carry Flag
                if((reg[L] & 0x0000000F) == 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case 45: // 2D DEC L
                reg[L] -= 1;
                // set Subtract Flag
                setSubtractFlag();
                // set Half Carry Flag
                if((reg[L] & 0x0000000F) == 0x0000000F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();

                // set Zero Flag
                if(reg[L] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                pc += 1;
                cycles += 4;
                break;
            case 46: // 2E LD L, d8
                reg[L] = Memory.read(pc+1);
                pc += 2;
                cycles += 8;
                break;
            case 47: // 2F CPL (Complement A)
                reg[A] ^= 0xFF;
                setSubtractFlag();
                setHalfCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case 48: // 30 JR NC, r8
                if((reg[F] & 0x00000001) == 0) {
                    pc += (byte)Memory.read(pc + 1);
                    cycles += 4;
                }
                pc += 2;
                cycles += 8;
                break;
            case 49: // 31 LD SP, d16
                sp = ((Memory.read(pc+2) << 8) | (Memory.read(pc+1) & 0xFF)) & 0x0000FFFF;
                pc += 3;
                cycles += 12;
                break;
            case 50: // 32 LD (HL-), A
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                Memory.write(address, reg[A]);
                // No need to set the Carry Flag
                if(reg[L] == 0)
                    reg[H] -= 1;
                reg[L] -= 1;
                pc += 1;
                cycles += 8;
                break;
            case 51: // 33 INC SP
                // No need to set the Carry Flag
                sp += 1;
                sp &= 0x0000FFFF;
                pc += 1;
                cycles += 8;
                break;
            case 52: // 34 INC (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                val = Memory.read(address);
                val += 1;
                // set Zero Flag
                if(val == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                // set Subtract Flag
                resetSubtractFlag();
                // set Half Carry Flag
                if((val & 0x0000000F) == 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();

                Memory.write(address,val);

                pc += 1;
                cycles += 12;
                break;
            case 53: // 35 DEC (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                val = Memory.read(address);
                val-= 1;
                // set Subtract Flag
                setSubtractFlag();
                // set Half Carry Flag
                if((val & 0x0000000F) == 0x0000000F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();

                // set Zero Flag
                if(val == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                Memory.write(address,val);

                pc += 1;
                cycles += 12;
                break;
            case 54: // 36 LD (HL), d8
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                Memory.write(address, Memory.read(pc+1));
                pc += 2;
                cycles += 12;
                break;
            case 55: // 37 SCF
                resetSubtractFlag();
                resetHalfCarryFlag();
                setCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case 56: // 38 JR C, r8
                if((reg[F] & 0x00000001) != 0) {
                    pc += (byte)Memory.read(pc + 1);
                    cycles += 4;
                }
                pc += 2;
                cycles += 8;
                break;
            case 57: // 39 ADD HL, SP
                temp = (short) (((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF);
                temp2 = (short) (sp&0x0FFFF);

                resetSubtractFlag();
                // set Half Carry Flag
                if((((temp & 0xFFF) + (temp2&0xFFF))&0x1000) != 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if((((temp & 0xFFFF) + (temp2&0xFFFF))&0x10000) != 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                temp += temp2;

                reg[H] = (byte) ((temp >>> 8) & 0x000000FF);
                reg[L] = (byte) (temp & 0x000000FF);
                pc += 1;
                cycles += 8;
                break;
            case 58: // 3A LD A, (HL-)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                reg[A] = Memory.read(address);
                // No need to set the Carry Flag
                if(reg[L] == 0)
                    reg[H] -= 1;
                reg[L] -= 1;
                pc += 1;
                cycles += 8;
                break;
            case 59: // 3B DEC SP
                sp -= 1;
                pc += 1;
                cycles += 8;
                break;
            case 60: // 3C INC A
                reg[A] += 1;
                // set Zero Flag
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                // set Subtract Flag
               resetSubtractFlag();
                // set Half Carry Flag
                if((reg[A] & 0x0000000F) == 0)
                   setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case 61: // 3D DEC A
                reg[A] -= 1;
                // set Subtract Flag
                setSubtractFlag();
                // set Half Carry Flag
                if((reg[A] & 0x0000000F) == 0x0000000F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();

                // set Zero Flag
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                pc += 1;
                cycles += 4;
                break;
            case 62: // 3E LD A, d8
                reg[A] = Memory.read(pc+1);
                pc += 2;
                cycles += 8;
                break;
            case 63: // 3F CCF (Complement carry flag)
                if((reg[F] & 0x00000001) == 0)
                    setCarryFlag();
                else
                    resetCarryFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();

                pc += 1;
                cycles += 4;
                break;
            case 64: // 40 LD B, B
                //reg[B] = reg[B];

                pc = pc+1;
                cycles += 4;
                break;
            case 65: // 41 LD B, C
                reg[B] = reg[C];
                pc += 1;
                cycles += 4;
                break;
            case 66: // 42 LD B, D
                reg[B] = reg[D];
                pc += 1;
                cycles += 4;
                break;
            case 67: // 43 LD B, E
                reg[B] = reg[E];
                pc += 1;
                cycles += 4;
                break;
            case 68: // 44 LD B, H
                reg[B] = reg[H];
                pc += 1;
                cycles += 4;
                break;
            case 69: // 45 LD B, L
                reg[B] = reg[L];
                pc += 1;
                cycles += 4;
                break;
            case 70: // 46 LD B, (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                reg[B] = Memory.read(address);
                pc += 1;
                cycles += 8;
                break;
            case 71: // 47 LD B, A
                reg[B] = reg[A];
                pc += 1;
                cycles += 4;
                break;
            case 72: // 48 LD C, B
                reg[C] = reg[B];
                pc += 1;
                cycles += 4;
                break;
            case 73: // 49 LD C, C
                //reg[C] = reg[C];
                pc ++;
                cycles += 4;
                break;
            case 74: // 4A LD C, D
                reg[C] = reg[D];
                pc += 1;
                cycles += 4;
                break;
            case 75: // 4B LD C, E
                reg[C] = reg[E];
                pc += 1;
                cycles += 4;
                break;
            case 76: // 4C LD C, H
                reg[C] = reg[H];
                pc += 1;
                cycles += 4;
                break;
            case 77: // 4D LD C, L
                reg[C] = reg[L];
                pc += 1;
                cycles += 4;
                break;
            case 78: // 4E LD C, (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                reg[C] = Memory.read(address);
                pc += 1;
                cycles += 8;
                break;
            case 79: // 4F LD C, A
                reg[C] = reg[A];
                pc += 1;
                cycles += 4;
                break;
            case 80: // 50 LD D, B
                reg[D] = reg[B];
                pc += 1;
                cycles += 4;
                break;
            case 81: // 51 LD D, C
                reg[D] = reg[C];
                pc += 1;
                cycles += 4;
                break;
            case 82: // 52 LD D, D
                //reg[D] = reg[D];
                ++pc;
                cycles += 4;
                break;
            case 83: // 53 LD D, E
                reg[D] = reg[E];
                pc += 1;
                cycles += 4;
                break;
            case 84: // 54 LD D, H
                reg[D] = reg[H];
                pc += 1;
                cycles += 4;
                break;
            case 85: // 55 LD D, L
                reg[D] = reg[L];
                pc += 1;
                cycles += 4;
                break;
            case 86: // 56 LD D, (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                reg[D] = Memory.read(address);
                pc += 1;
                cycles += 8;
                break;
            case 87: // 57 LD D, A
                reg[D] = reg[A];
                pc += 1;
                cycles += 4;
                break;
            case 88: // 58 LD E, B
                reg[E] = reg[B];
                pc += 1;
                cycles += 4;
                break;
            case 89: // 59 LD E, C
                reg[E] = reg[C];
                pc += 1;
                cycles += 4;
                break;
            case 90: // 5A LD E, D
                reg[E] = reg[D];
                pc += 1;
                cycles += 4;
                break;
            case 91: // 5B LD E, E
                //reg[E] = reg[E];
                pc += 1;
                cycles = cycles + 4;
                break;
            case 92: // 5C LD E, H
                reg[E] = reg[H];
                pc += 1;
                cycles += 4;
                break;
            case 93: // 5D LD E, L
                reg[E] = reg[L];
                pc += 1;
                cycles += 4;
                break;
            case 94: // 5E LD E, (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                reg[E] = Memory.read(address);
                pc += 1;
                cycles += 8;
                break;
            case 95: // 5F LD E, A
                reg[E] = reg[A];
                pc += 1;
                cycles += 4;
                break;
            case 96: // 60 LD H, B
                reg[H] = reg[B];
                pc += 1;
                cycles += 4;
                break;
            case 97: // 61 LD H, C
                reg[H] = reg[C];
                pc += 1;
                cycles += 4;
                break;
            case 98: // 62 LD H, D
                reg[H] = reg[D];
                pc += 1;
                cycles += 4;
                break;
            case 99: // 63 LD H, E
                reg[H] = reg[E];
                pc += 1;
                cycles += 4;
                break;
            case 100: // 64 LD H, H
                //reg[H] = reg[H];
                pc ++;
                cycles = cycles + 4;
                break;
            case 101: // 65 LD H, L
                reg[H] = reg[L];
                pc += 1;
                cycles += 4;
                break;
            case 102: // 66 LD H, (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                reg[H] = Memory.read(address);
                pc += 1;
                cycles += 8;
                break;
            case 103: // 67 LD H, A
                reg[H] = reg[A];
                pc += 1;
                cycles += 4;
                break;
            case 104: // 68 LD L, B
                reg[L] = reg[B];
                pc += 1;
                cycles += 4;
                break;
            case 105: // 69 LD L, C
                reg[L] = reg[C];
                pc += 1;
                cycles += 4;
                break;
            case 106: // 6A LD L, D
                reg[L] = reg[D];
                pc += 1;
                cycles += 4;
                break;
            case 107: // 6B LD L, E
                reg[L] = reg[E];
                pc += 1;
                cycles += 4;
                break;
            case 108: // 6C LD L, H
                reg[L] = reg[H];
                pc += 1;
                cycles += 4;
                break;
            case 109: // 6D LD L, L
                //reg[L] = reg[L];
                ++pc;
                cycles = cycles + 4;
                break;
            case 110: // 6E LD L, (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                reg[L] = Memory.read(address);
                pc += 1;
                cycles += 8;
                break;
            case 111: // 6F LD L, A
                reg[L] = reg[A];
                pc += 1;
                cycles += 4;
                break;
            case 112: // 70 LD (HL), B
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                Memory.write(address,reg[B]);
                pc += 1;
                cycles += 8;
                break;
            case 113: // 71 LD (HL), C
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                Memory.write(address,reg[C]);
                pc += 1;
                cycles += 8;
                break;
            case 114: // 72 LD (HL), D
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                Memory.write(address,reg[D]);
                //System.out.println("Writing: "+reg[D]);
                pc += 1;
                cycles += 8;
                break;
            case 115: // 73 LD (HL), E
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                Memory.write(address,reg[E]);
                pc += 1;
                cycles += 8;
                break;
            case 116: // 74 LD (HL), H
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                Memory.write(address,reg[H]);
                pc += 1;
                cycles += 8;
                break;
            case 117: // 75 LD (HC), L
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                Memory.write(address,reg[L]);
                pc += 1;
                cycles += 8;
                break;
            case 118: // 76 HALT
                //System.out.println("HALTING! take that Turing!");
                halted = true;
                //System.exit(0);
                pc += 1;
                cycles += 4;
                break;
            case 119: // 77 LD (HL), A
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                Memory.write(address,reg[A]);
                pc += 1;
                cycles += 8;
                break;
            case 120: // 78 LD A, B
                reg[A] = reg[B];
                pc += 1;
                cycles += 4;
                break;
            case 121: // 79 LD A, C
                reg[A] = reg[C];
                pc += 1;
                cycles += 4;
                break;
            case 122: // 7A LD A, D
                reg[A] = reg[D];
                pc += 1;
                cycles += 4;
                break;
            case 123: // 7B LD A, E
                reg[A] = reg[E];
                pc += 1;
                cycles += 4;
                break;
            case 124: // 7C LD A, H
                reg[A] = reg[H];
                pc += 1;
                cycles += 4;
                break;
            case 125: // 7D LD A, L
                reg[A] = reg[L];
                pc += 1;
                cycles += 4;
                break;
            case 126: // 7E LD A, (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                reg[A] = Memory.read(address);
                pc += 1;
                cycles += 8;
                break;
            case 127: // 7F LD A, A
                //reg[A] = reg[A];
                pc = pc + 1;
                cycles = cycles + 4;
                break;
            case -128: // 80 ADD A, B
                val = reg[B];
                resetSubtractFlag();
                // set Half Carry Flag
                if((((reg[A] & 0xF) + (val&0xF))&0x10) != 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if((((reg[A] & 0xFF) + (val&0xFF))&0x100) != 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                pc += 1;
                cycles += 4;
                break;
            case -127: // 81 ADD A, C
                val = reg[C];
                resetSubtractFlag();
                // set Half Carry Flag
                if((((reg[A] & 0xF) + (val&0xF))&0x10) != 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if((((reg[A] & 0xFF) + (val&0xFF))&0x100) != 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                pc += 1;
                cycles += 4;
                break;
            case -126: // 82 ADD A, D
                val = reg[D];
                resetSubtractFlag();
                // set Half Carry Flag
                if((((reg[A] & 0xF) + (val&0xF))&0x10) != 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if((((reg[A] & 0xFF) + (val&0xFF))&0x100) != 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                pc += 1;
                cycles += 4;
                break;
            case -125: // 83 ADD A, E
                val = reg[E];
                resetSubtractFlag();
                // set Half Carry Flag
                if((((reg[A] & 0xF) + (val&0xF))&0x10) != 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if((((reg[A] & 0xFF) + (val&0xFF))&0x100) != 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                pc += 1;
                cycles += 4;
                break;
            case -124: // 84 ADD A, H
                val = reg[H];
                resetSubtractFlag();
                // set Half Carry Flag
                if((((reg[A] & 0xF) + (val&0xF))&0x10) != 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if((((reg[A] & 0xFF) + (val&0xFF))&0x100) != 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                pc += 1;
                cycles += 4;
                break;
            case -123: // 85 ADD A, L
                val = reg[L];
                resetSubtractFlag();
                // set Half Carry Flag
                if((((reg[A] & 0xF) + (val&0xF))&0x10) != 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if((((reg[A] & 0xFF) + (val&0xFF))&0x100) != 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                pc += 1;
                cycles += 4;
                break;
            case -122: // 86 ADD A, (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                val = Memory.read(address);
                resetSubtractFlag();
                // set Half Carry Flag
                if((((reg[A] & 0xF) + (val&0xF))&0x10) != 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if((((reg[A] & 0xFF) + (val&0xFF))&0x100) != 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                pc += 1;
                cycles += 8;
                break;
            case -121: // 87 ADD A, A
                val = reg[A];
                resetSubtractFlag();
                // set Half Carry Flag
                if((((reg[A] & 0xF) + (val&0xF))&0x10) != 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if((((reg[A] & 0xFF) + (val&0xFF))&0x100) != 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                pc += 1;
                cycles += 4;
                break;
            case -120: // 88 ADC A, B
                val = reg[B];
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                resetSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) + (val & 0x0F) + carry) > 0x0F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) + (val & 0x0FF) + carry) > 0x0FF)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case -119: // 89 ADC A, C
                val = reg[C];
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                resetSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) + (val & 0x0F) + carry) > 0x0F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) + (val & 0x0FF) + carry) > 0x0FF)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case -118: // 8A ADC A, D
                val = reg[D];
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                resetSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) + (val & 0x0F) + carry) > 0x0F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) + (val & 0x0FF) + carry) > 0x0FF)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case -117: // 8B ADC A, E
                val = reg[E];
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                resetSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) + (val & 0x0F) + carry) > 0x0F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) + (val & 0x0FF) + carry) > 0x0FF)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case -116: // 8C ADC A, H
                val = reg[H];
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                resetSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) + (val & 0x0F) + carry) > 0x0F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) + (val & 0x0FF) + carry) > 0x0FF)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case -115: // 8D ADC A, L
                val = reg[L];
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                resetSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) + (val & 0x0F) + carry) > 0x0F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) + (val & 0x0FF) + carry) > 0x0FF)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case -114: // 8E ADC A, (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                val = Memory.read(address);
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                resetSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) + (val & 0x0F) + carry) > 0x0F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) + (val & 0x0FF) + carry) > 0x0FF)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 8;
                break;
            case -113: // 8D ADC A, A
                val = reg[A];
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                resetSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) + (val & 0x0F) + carry) > 0x0F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) + (val & 0x0FF) + carry) > 0x0FF)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case -112: // 90 SUB B
                val = reg[B];
                if((((reg[A] & 0xF) - (val & 0xF)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0xFF) - (val & 0xFF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                reg[A] -= val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 1;
                cycles += 4;
                break;
            case -111: // 91 SUB C
                val = reg[C];
                if((((reg[A] & 0xF) - (val & 0xF)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0xFF) - (val & 0xFF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                reg[A] -= val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 1;
                cycles += 4;
                break;
            case -110: // 92 SUB D
                val = reg[D];
                if((((reg[A] & 0xF) - (val & 0xF)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0xFF) - (val & 0xFF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                reg[A] -= val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 1;
                cycles += 4;
                break;
            case -109: // 93 SUB E
                val = reg[E];
                if((((reg[A] & 0xF) - (val & 0xF)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0xFF) - (val & 0xFF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                reg[A] -= val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 1;
                cycles += 4;
                break;
            case -108: // 94 SUB H
                val = reg[H];
                if((((reg[A] & 0xF) - (val & 0xF)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0xFF) - (val & 0xFF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                reg[A] -= val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 1;
                cycles += 4;
                break;
            case -107: // 95 SUB L
                val = reg[L];
                if((((reg[A] & 0xF) - (val & 0xF)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0xFF) - (val & 0xFF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                reg[A] -= val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 1;
                cycles += 4;
                break;
            case -106: // 96 SUB (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                val = Memory.read(address);
                if((((reg[A] & 0xF) - (val & 0xF)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0xFF) - (val & 0xFF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                reg[A] -= val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 1;
                cycles += 8;
                break;
            case -105: // 97 SUB A
                val = reg[A];
                if((((reg[A] & 0xF) - (val & 0xF)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0xFF) - (val & 0xFF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                reg[A] -= val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 1;
                cycles += 4;
                break;
            case -104: // 98 SBC A, B
                val = reg[B];
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                setSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) - (val & 0x0F) - carry) < 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) - (val & 0x0FF) - carry) < 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] -= val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case -103: // 99 SBC A, C
                val = reg[C];
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                setSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) - (val & 0x0F) - carry) < 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) - (val & 0x0FF) - carry) < 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] -= val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case -102: // 9A SBC A, D
                val = reg[D];
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                setSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) - (val & 0x0F) - carry) < 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) - (val & 0x0FF) - carry) < 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] -= val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case -101: // 9B SBC A, E
                val = reg[E];
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                setSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) - (val & 0x0F) - carry) < 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) - (val & 0x0FF) - carry) < 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] -= val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case -100: // 9C SBC A, H
                val = reg[H];
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                setSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) - (val & 0x0F) - carry) < 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) - (val & 0x0FF) - carry) < 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] -= val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case -99: // 9D SBC A, L
                val = reg[L];
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                setSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) - (val & 0x0F) - carry) < 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) - (val & 0x0FF) - carry) < 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] -= val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case -98: // 9E SBC A, (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                val = Memory.read(address);
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                setSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) - (val & 0x0F) - carry) < 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) - (val & 0x0FF) - carry) < 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] -= val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 8;
                break;
            case -97: // 9D SBC A, A
                val = reg[A];
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                setSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) - (val & 0x0F) - carry) < 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) - (val & 0x0FF) - carry) < 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] -= val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 1;
                cycles += 4;
                break;
            case -96: // A0 AND B
                reg[A] &= reg[B];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                setHalfCarryFlag();
                resetCarryFlag();

                pc += 1;
                cycles += 4;
                break;
            case -95: // A1 AND C
                reg[A] &= reg[C];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                setHalfCarryFlag();
                resetCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case -94: // A2 AND D
                reg[A] &= reg[D];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                setHalfCarryFlag();
                resetCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case -93: // A3 AND E
                reg[A] &= reg[E];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                setHalfCarryFlag();
                resetCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case -92: // A4 AND H
                reg[A] &= reg[H];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                setHalfCarryFlag();
                resetCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case -91: // A5 AND L
                reg[A] &= reg[L];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                setHalfCarryFlag();
                resetCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case -90: // A6 AND (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                reg[A] &= Memory.read(address);
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                setHalfCarryFlag();
                resetCarryFlag();
                pc += 1;
                cycles += 8;
                break;
            case -89: // A7 AND A
                reg[A] &= reg[A];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                setHalfCarryFlag();
                resetCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case -88: // A8 XOR B
                reg[A] ^= reg[B];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();

                pc += 1;
                cycles += 4;
                break;
            case -87: // A9 XOR C
                reg[A] ^= reg[C];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();

                pc += 1;
                cycles += 4;
                break;
            case -86: // AA XOR D
                reg[A] ^= reg[D];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();

                pc += 1;
                cycles += 4;
                break;
            case -85: // AB XOR E
                reg[A] ^= reg[E];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();

                pc += 1;
                cycles += 4;
                break;
            case -84: // AC XOR H
                reg[A] ^= reg[H];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();

                pc += 1;
                cycles += 4;
                break;
            case -83: // AD XOR L
                reg[A] ^= reg[L];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();

                pc += 1;
                cycles += 4;
                break;
            case -82: // AE XOR (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                reg[A] ^= Memory.read(address);
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();
                pc += 1;
                cycles += 8;
                break;
            case -81: // AF XOR A
                reg[A] ^= reg[A];

                setZeroFlag();
                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();

                pc += 1;
                cycles += 4;
                break;
            case -80: // B0 OR B
                reg[A] |= reg[B];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();

                pc += 1;
                cycles += 4;
                break;
            case -79: // B1 OR C
                reg[A] |= reg[C];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case -78: // B2 OR D
                reg[A] |= reg[D];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case -77: // B3 OR E
                reg[A] |= reg[E];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case -76: // B4 OR H
                reg[A] |= reg[H];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case -75: // B5 OR L
                reg[A] |= reg[L];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case -74: // B6 OR (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                reg[A] |= Memory.read(address);
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();
                pc += 1;
                cycles += 8;
                break;
            case -73: // B7 OR A
                reg[A] |= reg[A];
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();
                pc += 1;
                cycles += 4;
                break;
            case -72: // B8 CP B
                val = reg[B];
                if((((reg[A] & 0xF) - (val & 0xF)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0xFF) - (val & 0xFF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }

                if((reg[A] - val) == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 1;
                cycles += 4;
                break;
            case -71: // B9 CP C
                val = reg[C];
                if((((reg[A] & 0xF) - (val & 0xF)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0xFF) - (val & 0xFF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }

                if((reg[A] - val) == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 1;
                cycles += 4;
                break;
            case -70: // BA CP D
                val = reg[D];
                if((((reg[A] & 0xF) - (val & 0xF)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0xFF) - (val & 0xFF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }

                if((reg[A] - val) == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 1;
                cycles += 4;
                break;
            case -69: // BB CP E
                val = reg[E];
                if((((reg[A] & 0x0F) - (val & 0x0F)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0x0FF) - (val & 0x0FF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                if((reg[A] - val) == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                setSubtractFlag();
                pc += 1;
                cycles += 4;
                break;
            case -68: // BC CP H
                val = reg[H];
                if((((reg[A] & 0xF) - (val & 0xF)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0xFF) - (val & 0xFF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }

                if((reg[A] - val) == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 1;
                cycles += 4;
                break;
            case -67: // BD CP L
                val = reg[L];
                if((((reg[A] & 0xF) - (val & 0xF)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0xFF) - (val & 0xFF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }

                if((reg[A] - val) == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 1;
                cycles += 4;
                break;
            case -66: // BE CP (HL)
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                val = Memory.read(address);
                //System.out.println("Comparing: "+val+" "+reg[A]);
                if((((reg[A] & 0xF) - (val & 0x0F)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0xFF) - (val & 0x0FF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }

                if((reg[A] - (val)) == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 1;
                cycles += 8;
                break;
            case -65: // BF CP A
                val = reg[A];
                if((((reg[A] & 0xF) - (val & 0xF)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0xFF) - (val & 0xFF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }

                if((reg[A] - val) == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 1;
                cycles += 4;
                break;
            case -64: // C0 RET NZ
                if((reg[F] & 0x00000008) == 0) {
                    address = ((Memory.read(sp + 1) << 8) | (Memory.read(sp) & 0xFF)) & 0x0000FFFF;
                    sp += 2;
                    pc = address;
                    cycles += 24;
                }else{
                    pc += 1;
                    cycles += 8;
                }
                break;
            case -63: // C1 POP BC
                reg[B] = Memory.read(sp+1);
                reg[C] = Memory.read(sp);
                sp += 2;
                pc += 1;
                cycles += 12;
                break;
            case -62: // C2 JP NZ, a16
                if((reg[F] & 0x00000008) == 0) {
                    address = ((Memory.read(pc + 2) << 8) | (Memory.read(pc + 1) & 0xFF)) & 0x0000FFFF;
                    pc = address;
                    cycles += 16;
                }else{
                    pc += 3;
                    cycles += 12;
                }
                break;
            case -61: // C3 JP a16
                address = ((Memory.read(pc+2) << 8) | (Memory.read(pc+1) & 0xFF)) & 0x0000FFFF;
                pc = address;
                cycles += 16;
                break;
            case -60: // C4 CALL NZ, a16
                if((reg[F] & 0x00000008) == 0) {
                    address = ((Memory.read(pc + 2) << 8) | (Memory.read(pc + 1) & 0xFF)) & 0x0000FFFF;
                    Memory.write(sp - 1, (byte) (((pc + 3) >>> 8) & 0x000000FF));
                    Memory.write(sp - 2, (byte) ((pc + 3) & 0x000000FF));
                    sp -= 2;
                    pc = address;
                    cycles += 24;
                }else{
                    pc += 3;
                    cycles += 12;
                }
                break;
            case -59: // C5 PUSH BC
                Memory.write(sp-1, reg[B]);
                Memory.write(sp-2, reg[C]);
                sp -= 2;
                pc += 1;
                cycles += 16;
                break;
            case -58: // C6 ADD A, d8
                val = Memory.read(pc+1);
                resetSubtractFlag();
                // set Half Carry Flag
                if((((reg[A] & 0xF) + (val&0xF))&0x10) != 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if((((reg[A] & 0xFF) + (val&0xFF))&0x100) != 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                pc += 2;
                cycles += 8;
                break;
            case -57: // C7 RST 00H
                Memory.write(sp-1, (byte)(((pc+1) >>> 8) & 0x000000FF));
                Memory.write(sp-2, (byte)((pc+1) & 0x000000FF));
                sp -= 2;
                pc = 0x000;
                cycles += 16;
                break;
            case -56: // C8 RET Z
                if((reg[F] & 0x00000008) != 0) {
                    address = ((Memory.read(sp + 1) << 8) | (Memory.read(sp) & 0xFF)) & 0x0000FFFF;
                    sp += 2;
                    pc = address;
                    cycles += 24;
                }else{
                    pc += 1;
                    cycles += 8;
                }
                break;
            case -55: // C9 RET
                address = ((Memory.read(sp+1) << 8) | (Memory.read(sp) & 0xFF)) & 0x0000FFFF;
                sp += 2;
                pc = address;
                cycles += 16;
                break;
            case -54: // CA JP Z, a16
                if((reg[F] & 0x00000008) != 0) {
                    address = ((Memory.read(pc + 2) << 8) | (Memory.read(pc + 1) & 0xFF)) & 0x0000FFFF;
                    pc = address;
                    cycles += 16;
                }else{
                    pc += 3;
                    cycles += 12;
                }
                break;
            case -53: // CB PREFIX
                cycles += executeCBInst(Memory.read(pc+1));
                pc += 2;
                cycles += 12;
                break;
            case -52: // CC CALL Z, a16
                if((reg[F] & 0x00000008) != 0) {
                    address = ((Memory.read(pc + 2) << 8) | (Memory.read(pc + 1) & 0xFF)) & 0x0000FFFF;
                    Memory.write(sp - 1, (byte) (((pc + 3) >>> 8) & 0x000000FF));
                    Memory.write(sp - 2, (byte) ((pc + 3) & 0x000000FF));
                    sp -= 2;
                    pc = address;
                    cycles += 24;
                }else{
                    pc += 3;
                    cycles += 12;
                }
                break;
            case -51: // CD CALL a16
                address = ((Memory.read(pc+2) << 8) | (Memory.read(pc+1) & 0xFF)) & 0x0000FFFF;
                Memory.write(sp-1, (byte)(((pc+3) >>> 8) & 0x000000FF));
                Memory.write(sp-2, (byte)((pc+3) & 0x000000FF));
                sp -= 2;
                pc = address;
                cycles += 24;
                break;
            case -50: // CE ADC A, d8
                val = Memory.read(pc+1);
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                resetSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) + (val & 0x0F) + carry) > 0x0F)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) + (val & 0x0FF) + carry) > 0x0FF)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] += val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 2;
                cycles += 8;
                break;
            case -49: // CF RST 08H
                Memory.write(sp-1, (byte)(((pc+1) >>> 8) & 0x000000FF));
                Memory.write(sp-2, (byte)((pc+1) & 0x000000FF));
                sp -= 2;
                pc = 0x008;
                cycles += 16;
                break;
            case -48: // D0 RET NC
                if((reg[F] & 0x00000001) == 0) {
                    address = ((Memory.read(sp + 1) << 8) | (Memory.read(sp) & 0xFF)) & 0x0000FFFF;
                    sp += 2;
                    pc = address;
                    cycles += 24;
                }else{
                    pc += 1;
                    cycles += 8;
                }
                break;
            case -47: // D1 POP DE
                reg[D] = Memory.read(sp+1);
                reg[E] = Memory.read(sp);
                sp += 2;
                pc += 1;
                cycles += 12;
                break;
            case -46: // D2 JP NC, a16
                if((reg[F] & 0x00000001) == 0) {
                    address = ((Memory.read(pc + 2) << 8) | (Memory.read(pc + 1) & 0xFF)) & 0x0000FFFF;
                    pc = address;
                    cycles += 16;
                }else{
                    pc += 3;
                    cycles += 12;
                }
                break;
            case -44: // D4 CALL NC, a16
                if((reg[F] & 0x00000001) == 0) {
                    address = ((Memory.read(pc + 2) << 8) | (Memory.read(pc + 1) & 0xFF)) & 0x0000FFFF;
                    Memory.write(sp - 1, (byte) (((pc + 3) >>> 8) & 0x000000FF));
                    Memory.write(sp - 2, (byte) ((pc + 3) & 0x000000FF));
                    sp -= 2;
                    pc = address;
                    cycles += 24;
                }else{
                    pc += 3;
                    cycles += 12;
                }
                break;
            case -43: // D5 PUSH DE
                Memory.write(sp-1, reg[D]);
                Memory.write(sp-2, reg[E]);
                sp -= 2;
                pc += 1;
                cycles += 16;
                break;
            case -42: // D6 SUB d8
                val = Memory.read(pc+1);
                if((((reg[A] & 0xF) - (val & 0xF)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0xFF) - (val & 0xFF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                reg[A] -= val;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 2;
                cycles += 8;
                break;
            case -41: // D7 RST 10H
                Memory.write(sp-1, (byte)(((pc+1) >>> 8) & 0x000000FF));
                Memory.write(sp-2, (byte)((pc+1) & 0x000000FF));
                sp -= 2;
                pc = 0x010;
                cycles += 16;
                break;
            case -40: // D8 RET C
                if((reg[F] & 0x00000001) != 0) {
                    address = ((Memory.read(sp + 1) << 8) | (Memory.read(sp) & 0xFF)) & 0x0000FFFF;
                    sp += 2;
                    pc = address;
                    cycles += 24;
                }else{
                    pc += 1;
                    cycles += 8;
                }
                break;
            case -39: // D9 RETI
                address = ((Memory.read(sp+1) << 8) | (Memory.read(sp) & 0xFF)) & 0x0000FFFF;
                sp += 2;
                pc = address;
                interruptsEnabled = true;
                cycles += 16;
                break;
            case -38: // DA JP C, a16
                if((reg[F] & 0x00000001) != 0) {
                    address = ((Memory.read(pc + 2) << 8) | (Memory.read(pc + 1) & 0xFF)) & 0x0000FFFF;
                    pc = address;
                    cycles += 16;
                }else{
                    pc += 3;
                    cycles += 12;
                }
                break;
            case -36: // DC CALL C, a16
                if((reg[F] & 0x00000001) != 0) {
                    address = ((Memory.read(pc + 2) << 8) | (Memory.read(pc + 1) & 0xFF)) & 0x0000FFFF;
                    Memory.write(sp - 1, (byte) (((pc + 3) >>> 8) & 0x000000FF));
                    Memory.write(sp - 2, (byte) ((pc + 3) & 0x000000FF));
                    sp -= 2;
                    pc = address;
                    cycles += 24;
                }else{
                    pc += 3;
                    cycles += 12;
                }
                break;
            case -34: // DE SBC A, d8
                val = Memory.read(pc+1);
                carry = 0;
                if((reg[F] & 0x00000001) != 0)
                    carry = 1;

                setSubtractFlag();
                // set Half Carry Flag
                if(((reg[A] & 0x0F) - (val & 0x0F) - carry) < 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if(((reg[A] & 0x0FF) - (val & 0x0FF) - carry) < 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[A] -= val + carry;
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                pc += 2;
                cycles += 8;
                break;
            case -33: // DF RST 18H
                Memory.write(sp-1, (byte)(((pc+1) >>> 8) & 0x000000FF));
                Memory.write(sp-2, (byte)((pc+1) & 0x000000FF));
                sp -= 2;
                pc = 0x018;
                cycles += 16;
                break;
            case -32: // E0 LDH (a8), A
                address = (0x0000FF00 | (Memory.read(pc+1) & 0xFF)) & 0x0000FFFF;
                Memory.write(address, reg[A]);
                pc += 2;
                cycles += 12;
                break;
            case -31: // E1 POP HL
                reg[H] = Memory.read(sp+1);
                reg[L] = Memory.read(sp);
                sp += 2;
                pc += 1;
                cycles += 12;
                break;
            case -30: // E2 LD (C), A
                address = (reg[C] & 0x000000FF) | 0x0000FF00;
                Memory.write(address, reg[A]);
                pc += 1;
                cycles += 8;
                break;
            case -27: // E5 PUSH HL
                Memory.write(sp-1, reg[H]);
                Memory.write(sp-2, reg[L]);
                sp -= 2;
                pc += 1;
                cycles += 16;
                break;
            case -26: // E6 AND d8
                reg[A] &= Memory.read(pc+1);
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                setHalfCarryFlag();
                resetCarryFlag();

                pc += 2;
                cycles += 8;
                break;
            case -25: // E7 RST 20H
                Memory.write(sp-1, (byte)(((pc+1) >>> 8) & 0x000000FF));
                Memory.write(sp-2, (byte)((pc+1) & 0x000000FF));
                sp -= 2;
                pc = 0x020;
                cycles += 16;
                break;
            case -24: // E8 ADD SP, r8
                val = Memory.read(pc+1);
                resetSubtractFlag();
                // set Half Carry Flag
                if((((sp & 0xF) + (val&0xF))&0x10) != 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if((((sp & 0xFF) + (val&0xFF))&0x100) != 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                sp += val;

                resetZeroFlag();
                resetSubtractFlag();
                pc += 2;
                cycles += 16;
                break;
            case -23: // E9 JP HL
                address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                pc = address;
                cycles += 4;
                break;
            case -22: // EA LD (a16), A
                address = ((Memory.read(pc+2) << 8) | (Memory.read(pc+1) & 0xFF)) & 0x0000FFFF;
                Memory.write(address, reg[A]);
                pc += 3;
                cycles += 16;
                break;
            case -18: // EE XOR d8
                reg[A] ^= Memory.read(pc+1);
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();

                pc += 2;
                cycles += 8;
                break;
            case -17: // EF RST 28H
                Memory.write(sp-1, (byte)(((pc+1) >>> 8) & 0x000000FF));
                Memory.write(sp-2, (byte)((pc+1) & 0x000000FF));
                sp -= 2;
                pc = 0x028;
                cycles += 16;
                break;
            case -16: // F0 LDH A, (a8)
                address = (0x0000FF00 | (Memory.read(pc+1) & 0xFF)) & 0x0000FFFF;
                reg[A] = Memory.read(address);
                pc += 2;
                cycles += 12;
                break;
            case -15: // F1 POP AF
                reg[A] = Memory.read(sp+1);
                reg[F] = (byte)(Memory.read(sp) >>> 4);
                sp += 2;
                pc += 1;
                cycles += 12;
                break;
            case -14: // F2 LD A, (C)
                address = (reg[C] & 0x000000FF) | 0x0000FF00;
                reg[A] = Memory.read(address);
                pc += 1;
                cycles += 8;
                break;
            case -13: // F3 DI
                interruptsEnabled = false;
                pc += 1;
                cycles += 4;
                break;
            case -11: // F5 PUSH AF
                Memory.write(sp-1, reg[A]);
                Memory.write(sp-2, (byte)(reg[F]<<4));
                sp -= 2;
                pc += 1;
                cycles += 16;
                break;
            case -10: // F6 OR d8
                reg[A] |= Memory.read(pc+1);
                if(reg[A] == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();

                pc += 2;
                cycles += 8;
                break;
            case -9: // F7 RST 30H
                Memory.write(sp-1, (byte)(((pc+1) >>> 8) & 0x000000FF));
                Memory.write(sp-2, (byte)((pc+1) & 0x000000FF));
                sp -= 2;
                pc = 0x030;
                cycles += 16;
                break;
            case -8: // F8 LD HL, SP + r8
                val = Memory.read(pc+1);
                resetSubtractFlag();
                // set Half Carry Flag
                if((((sp & 0xF) + (val&0xF))&0x10) != 0)
                    setHalfCarryFlag();
                else
                    resetHalfCarryFlag();
                // set Carry Flag
                if((((sp & 0xFF) + (val&0xFF))&0x100) != 0)
                    setCarryFlag();
                else
                    resetCarryFlag();
                reg[H] = (byte) ((sp+val)>>> 8);
                reg[L] = (byte) (sp+val);


                resetZeroFlag();
                resetSubtractFlag();
                pc += 2;
                cycles += 12;
                break;
            case -7: // F9 LD SP, HL
                sp = (short)((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
                pc += 1;
                cycles += 8;
                break;
            case -6: // FA LD A, (a16)
                address = ((Memory.read(pc+2) << 8) | (Memory.read(pc+1) & 0xFF)) & 0x0000FFFF;
                reg[A] = Memory.read(address);
                pc += 3;
                cycles += 16;
                break;
            case -5: // FB EI
                enableInterruptsNextInst = true;
                pc += 1;
                cycles += 4;
                break;
            case -2: // FE CP d8
                val = Memory.read(pc+1);
                if((((reg[A] & 0x0F) - (val & 0x0F)) & 0x10) !=0){
                    setHalfCarryFlag();
                }else{
                    resetHalfCarryFlag();
                }
                if((((reg[A] & 0x0FF) - (val & 0x0FF)) & 0x100) !=0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }

                if((reg[A] - val) == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();
                setSubtractFlag();
                pc += 2;
                cycles += 8;
                break;
            case -1: // FF RST 38H
                Memory.write(sp-1, (byte)(((pc+1) >>> 8) & 0x000000FF));
                Memory.write(sp-2, (byte)((pc+1) & 0x000000FF));
                sp -= 2;
                pc = 0x038;
                cycles += 16;
                break;
            default:
                System.out.println("UNDEFINED OPCODE: "+Integer.toHexString(opcode) + " at PC=" +pc);
                pc += 1;
                cycles += 4;
                break;

        }
        //logStatus();

        if(oldPC == pc){
            //System.err.println("WARNING! PC wasn't incremented: "+pc+" : "+opcode);
        }

        return cycles;
    }

    private int executeCBInst(byte opcode){
        int cycles = 0;
        int index = opcode & 0x00000007;
        byte val;
        byte oldVal;
        // Read value from selected register
        if(index == 6){
            int address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
            val = Memory.read(address);
            cycles += 4;
        }else{
            val = reg[index];
        }
        // Decode the operation needed to be performed
        int op = opcode & 0x000000F8;
        switch(op){
            case 0x00: // RLC
                if((val & 0x00000080) != 0){
                    setCarryFlag();
                    val <<= 1;
                    val |= 0x00000001;
                }else{
                    resetCarryFlag();
                    val <<= 1;
                    val &= 0x000000FE;
                }

                if(val == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                if(index == 6)
                    cycles += 4;
                break;
            case 0x08: // RRC
                if((val & 0x00000001) != 0){
                    setCarryFlag();
                    val >>>= 1;
                    val |= 0x00000080;
                }else{
                    resetCarryFlag();
                    val >>>= 1;
                    val &= 0x0000007F;
                }

                if(val == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                if(index == 6)
                    cycles += 4;
                break;
            case 0x10: // RL
                oldVal = val;

                val = (byte) ((val << 1) & 0x000000FE);
                val &= 0x000000FE;
                val |= (reg[F] & 0x00000001);
                if((oldVal & 0x00000080) != 0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                if(val == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                if(index == 6)
                    cycles += 4;
                break;
            case 0x18: // RR
                oldVal = val;

                val = (byte) ((val >>> 1) & 0x0000007F);
                val &= 0x0000007F;
                val |= ((reg[F] & 0x00000001) << 7);
                if((oldVal & 0x00000001) != 0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                if(val == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                if(index == 6)
                    cycles += 4;
                break;
            case 0x20: // SLA (Shift left into carry, LSB = 0)
                if((val & 0x00000080) != 0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                val <<= 1;
                val &= 0xFE;

                if(val == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                if(index == 6)
                    cycles += 4;
                break;
            case 0x28: // SRA
                if((val & 0x00000001) != 0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                val >>= 1;
                val &= 0x7F;
                val |= (val << 1) & 0x00000080;

                if(val == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                if(index == 6)
                    cycles += 4;
                break;
            case 0x30: // SWAP
                val = (byte) (((val << 4) & 0x0F0) | ((val >>> 4)&0x0000000F));
                if(val == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                resetCarryFlag();

                break;
            case 0x38: // SRL
                if((val & 0x00000001) != 0){
                    setCarryFlag();
                }else{
                    resetCarryFlag();
                }
                val >>>= 1;
                val &= 0x7F;


                if(val == 0)
                    setZeroFlag();
                else
                    resetZeroFlag();

                resetSubtractFlag();
                resetHalfCarryFlag();
                if(index == 6)
                    cycles += 4;
                break;
            case 0x40: // BIT (Test Bit)
            case 0x48:
            case 0x50:
            case 0x58:
            case 0x60:
            case 0x68:
            case 0x70:
            case 0x78:
                int bit = (opcode & 0x00000038) >>> 3 ;
                if(((val >>> bit) & 0x00000001) == 0){
                    setZeroFlag();
                }else{
                    resetZeroFlag();
                }

                resetSubtractFlag();
                setHalfCarryFlag();
                break;
            case 0x80: // RES (Reset Bit)
            case 0x88:
            case 0x90:
            case 0x98:
            case 0xA0:
            case 0xA8:
            case 0xB0:
            case 0xB8:
                int bit2 = (opcode & 0x00000038) >> 3 ;
                val &= ~(1 << bit2);
                break;
            case 0xC0: // SET (Set Bit)
            case 0xC8:
            case 0xD0:
            case 0xD8:
            case 0xE0:
            case 0xE8:
            case 0xF0:
            case 0xF8:
                int bit1 = (opcode & 0x00000038) >> 3 ;
                val |= 0x00000001 << bit1;
                break;
            default:
                System.out.println("UNDEFINED SUFFIX: "+Integer.toHexString(opcode) + ":"+Integer.toHexString(op));
                break;


        }

        // Write result to correct register
        if(index == 6){
            int address = ((reg[H] << 8) | (reg[L] & 0xFF))& 0x0000FFFF;
            Memory.write(address, val);
        }else{
            reg[index] = val;
        }
        return cycles;
    }
    private void setZeroFlag(){
        reg[F] |= 0x00000008;
    }
    private void resetZeroFlag(){
        reg[F] &= 0x000000F7;
    }
    private void setSubtractFlag(){
        reg[F] |= 0x00000004;
    }
    private void resetSubtractFlag(){
        reg[F] &= 0x000000FB;
    }
    private void setHalfCarryFlag(){
        reg[F] |= 0x00000002;
    }
    private void resetHalfCarryFlag(){
        reg[F] &= 0x000000FD;
    }
    private void setCarryFlag(){
        reg[F] |= 0x00000001;
    }
    private void resetCarryFlag(){
        reg[F] &= 0x000000FE;
    }

    private void updateTimer(int cycles){
        for(int i=0;i<cycles/4;i++) {
            Memory.tickTimer();
        }
    }

    private void logStatus(){
        String status = "";
        status += "A: " + fmt(reg[A]) + " ";
        status += "F: " + fmt((byte)(reg[F]<<4)) + " ";
        status += "B: " + fmt(reg[B]) + " ";
        status += "C: " + fmt(reg[C]) + " ";
        status += "D: " + fmt(reg[D]) + " ";
        status += "E: " + fmt(reg[E]) + " ";
        status += "H: " + fmt(reg[H]) + " ";
        status += "L: " + fmt(reg[L]) + " ";
        status += "SP: " + fmt((short)sp) + " ";
        status += "PC: 00:" + fmt(pc) + " ";
        status += "(" + fmt(Memory.read(pc)) + " ";
        status += "" + fmt(Memory.read(pc+1)) + " ";
        status += "" + fmt(Memory.read(pc+2)) + " ";
        status += "" + fmt(Memory.read(pc+3)) + ")\n";

        pw.print(status);
        pw.flush();
    }
    private String fmt(byte val){
        String s = "00"+Integer.toHexString(val);
        return s.substring(s.length()-2).toUpperCase(Locale.ROOT);
    }

    private String fmt(short val){
        String s = "0000"+Integer.toHexString(val);
        return s.substring(s.length()-4).toUpperCase(Locale.ROOT);
    }
    private String fmt(int val){
        String s = "0000"+Integer.toHexString(val);
        return s.substring(s.length()-4).toUpperCase(Locale.ROOT);
    }
}
