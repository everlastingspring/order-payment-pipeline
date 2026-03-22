package com.paymentplatform.walletservice.application.mapper;

import com.paymentplatform.commonlib.dto.LedgerEntryDto;
import com.paymentplatform.commonlib.dto.MoneyDto;
import com.paymentplatform.commonlib.dto.WalletDto;
import com.paymentplatform.walletservice.domain.entity.LedgerEntry;
import com.paymentplatform.walletservice.domain.entity.Wallet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface WalletMapper {

    @Mapping(target = "walletId", source = "id")
    @Mapping(target = "balance", source = ".", qualifiedByName = "toBalanceMoney")
    @Mapping(target = "recentTransactions", source = "ledgerEntries")
    WalletDto toDto(Wallet wallet);

    @Mapping(target = "entryId", source = "id")
    @Mapping(target = "amount", source = ".", qualifiedByName = "toEntryMoney")
    LedgerEntryDto toEntryDto(LedgerEntry entry);

    List<LedgerEntryDto> toEntryDtos(List<LedgerEntry> entries);

    @Named("toBalanceMoney")
    default MoneyDto toBalanceMoney(Wallet wallet) {
        return MoneyDto.builder()
                .amount(wallet.getBalance())
                .currency(wallet.getCurrency())
                .build();
    }

    @Named("toEntryMoney")
    default MoneyDto toEntryMoney(LedgerEntry entry) {
        return MoneyDto.builder()
                .amount(entry.getAmount())
                .currency(entry.getCurrency())
                .build();
    }
}
