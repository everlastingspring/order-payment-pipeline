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

/**
 * MapStruct mapper converting {@link Wallet} and {@link LedgerEntry} entities to DTOs.
 *
 * <p><strong>Custom mappings:</strong></p>
 * <ul>
 *   <li>{@code wallet.id} → {@code walletId}</li>
 *   <li>{@code wallet.balance} + {@code wallet.currency} → {@code MoneyDto} via
 *       {@link #toBalanceMoney(Wallet)}</li>
 *   <li>{@code wallet.ledgerEntries} → {@code recentTransactions} (same data, renamed field)</li>
 *   <li>{@code entry.id} → {@code entryId}</li>
 *   <li>{@code entry.amount} + {@code entry.currency} → {@code MoneyDto} via
 *       {@link #toEntryMoney(LedgerEntry)}</li>
 * </ul>
 *
 * <p>The {@link Wallet#getLedgerEntries()} association is {@code LAZY} loaded. Callers must ensure
 * the session is open when this mapper is invoked (i.e., call within a {@code @Transactional} method
 * that loaded the wallet with its entries), otherwise MapStruct will trigger a
 * {@code LazyInitializationException}.</p>
 */
@Mapper(componentModel = "spring")
public interface WalletMapper {

    /**
     * Maps a {@link Wallet} entity to its DTO, including recent ledger entries.
     *
     * @param wallet the wallet entity with loaded {@code ledgerEntries}
     * @return populated {@link com.paymentplatform.commonlib.dto.WalletDto}
     */
    @Mapping(target = "walletId", source = "id")
    @Mapping(target = "balance", source = ".", qualifiedByName = "toBalanceMoney")
    @Mapping(target = "recentTransactions", source = "ledgerEntries")
    WalletDto toDto(Wallet wallet);

    /**
     * Maps a single {@link LedgerEntry} entity to its DTO.
     *
     * @param entry the ledger entry entity
     * @return populated {@link com.paymentplatform.commonlib.dto.LedgerEntryDto}
     */
    @Mapping(target = "entryId", source = "id")
    @Mapping(target = "amount", source = ".", qualifiedByName = "toEntryMoney")
    LedgerEntryDto toEntryDto(LedgerEntry entry);

    /**
     * Maps a list of ledger entries to DTOs.
     *
     * @param entries list of ledger entry entities
     * @return list of DTOs
     */
    List<LedgerEntryDto> toEntryDtos(List<LedgerEntry> entries);

    /**
     * Constructs a {@link MoneyDto} from the wallet's balance and currency.
     * Used by MapStruct via the {@code @Named("toBalanceMoney")} qualifier.
     *
     * @param wallet the wallet entity
     * @return balance as {@link MoneyDto}
     */
    @Named("toBalanceMoney")
    default MoneyDto toBalanceMoney(Wallet wallet) {
        return MoneyDto.builder()
                .amount(wallet.getBalance())
                .currency(wallet.getCurrency())
                .build();
    }

    /**
     * Constructs a {@link MoneyDto} from a ledger entry's amount and currency.
     * Used by MapStruct via the {@code @Named("toEntryMoney")} qualifier.
     *
     * @param entry the ledger entry entity
     * @return amount as {@link MoneyDto}
     */
    @Named("toEntryMoney")
    default MoneyDto toEntryMoney(LedgerEntry entry) {
        return MoneyDto.builder()
                .amount(entry.getAmount())
                .currency(entry.getCurrency())
                .build();
    }
}
