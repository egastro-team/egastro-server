package com.egastro.service;

import com.egastro.entity.Cart;
import com.egastro.entity.CartItem;
import com.egastro.exception.ApiException;
import com.egastro.mapper.CartMapper;
import com.egastro.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.egastro.testData.TestFixtures.*;
import static com.egastro.testData.ResponseTestData.cartProduct;
import static com.egastro.support.TestAssertions.assertApiError;
import static com.egastro.testData.RequestTestData.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {
    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Spy private CartMapper cartMapper;
    @InjectMocks private CartService service;

    @Test
    void shouldCreateEmptyCart_When_UserHasNoCart() {
        // GIVEN
        when(userRepository.findById(1L)).thenReturn(Optional.of(user()));
        when(cartItemRepository.findItemsWithProductsByUserId(1L)).thenReturn(List.of());

        // WHEN
        var response = service.get(1L);

        // THEN
        assertAll(() -> assertTrue(response.items().isEmpty()),
                () -> assertEquals(0, response.totalQuantity()),
                () -> assertEquals(new BigDecimal("0.00"), response.totalAmount()));
        var captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository, times(1)).save(captor.capture());
        assertEquals(1L, captor.getValue().getUser().getId());
    }

    @Test
    void shouldReuseCart_When_CartAlreadyExists() {
        // GIVEN
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart()));
        when(cartItemRepository.findItemsWithProductsByUserId(1L)).thenReturn(List.of(item(2)));

        // WHEN
        var response = service.get(1L);

        // THEN
        assertEquals(new BigDecimal("4.98"), response.totalAmount());
        verify(cartRepository, never()).save(any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectMissingUser_When_CreatingCart() {
        // GIVEN
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // WHEN
        var error = assertThrows(ApiException.class, () -> service.get(1L));

        // THEN
        assertApiError(error, NOT_FOUND, "User not found");
        verify(cartRepository, never()).save(any());
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#quantityBoundaries")
    void shouldCreateItem_When_QuantityIsAtInclusiveBoundary(int quantity) {
        // GIVEN
        when(productRepository.findById(3L)).thenReturn(Optional.of(product()));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart()));

        // WHEN
        service.addItem(1L, addCartItemRequest(3L, quantity));

        // THEN
        var captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository, times(1)).save(captor.capture());
        assertAll(() -> assertEquals(quantity, captor.getValue().getQuantity()),
                () -> assertEquals(3L, captor.getValue().getProduct().getId()),
                () -> assertEquals(4L, captor.getValue().getCart().getId()));
    }

    @Test
    void shouldAddQuantities_When_ItemAlreadyExists() {
        // GIVEN
        var item = item(98);
        when(productRepository.findById(3L)).thenReturn(Optional.of(item.getProduct()));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(item.getCart()));
        when(cartItemRepository.findByCartIdAndProductId(4L, 3L)).thenReturn(Optional.of(item));

        // WHEN
        service.addItem(1L, addCartItemRequest(3L, 1));

        // THEN
        assertEquals(99, item.getQuantity());
        verify(cartItemRepository, times(1)).save(item);
    }

    @Test
    void shouldLeaveItemUnchanged_When_AdditionExceedsLimit() {
        // GIVEN
        var item = item(99);
        when(productRepository.findById(3L)).thenReturn(Optional.of(item.getProduct()));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(item.getCart()));
        when(cartItemRepository.findByCartIdAndProductId(4L, 3L)).thenReturn(Optional.of(item));

        // WHEN
        var error = assertThrows(ApiException.class,
                () -> service.addItem(1L, addCartItemRequest(3L, 1)));

        // THEN
        assertApiError(error, BAD_REQUEST, "Validation failed");
        assertEquals("Maximum quantity per product is 99", error.getFieldErrors().getFirst().message());
        assertEquals(99, item.getQuantity());
        verify(cartItemRepository, never()).save(any());
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#invalidQuantities")
    void shouldRejectQuantity_When_AddOrUpdateInputIsInvalid(Integer quantity, String message) {
        // GIVEN
        var add = addCartItemRequest(3L, quantity);
        var update = updateCartItemRequest(quantity);

        // WHEN
        var addError = assertThrows(ApiException.class, () -> service.addItem(1L, add));
        var updateError = assertThrows(ApiException.class, () -> service.updateItem(1L, 3L, update));

        // THEN
        for (var error : List.of(addError, updateError)) {
            assertApiError(error, BAD_REQUEST, "Validation failed");
            assertEquals("quantity", error.getFieldErrors().getFirst().field());
            assertEquals(message, error.getFieldErrors().getFirst().message());
        }
        verifyNoInteractions(cartRepository, cartItemRepository, productRepository);
    }

    @Test
    void shouldRejectAdd_When_ProductDoesNotExist() {
        // GIVEN
        var request = addCartItemRequest(3L, 1);

        // WHEN
        var error = assertThrows(ApiException.class, () -> service.addItem(1L, request));

        // THEN
        assertApiError(error, NOT_FOUND, "Product not found");
        verifyNoInteractions(cartRepository, cartItemRepository);
    }

    @Test
    void shouldRejectAdd_When_ProductUnavailable() {
        // GIVEN
        var product = unavailableProduct();
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));

        // WHEN
        var error = assertThrows(ApiException.class,
                () -> service.addItem(1L, addCartItemRequest(3L, 1)));

        // THEN
        assertApiError(error, CONFLICT, "Product is currently unavailable");
        verifyNoInteractions(cartRepository, cartItemRepository);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#quantityBoundaries")
    void shouldSetQuantity_When_ItemExistsEvenIfUnavailable(int quantity) {
        // GIVEN
        var item = unavailableItem(2);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(item.getCart()));
        when(cartItemRepository.findByCartIdAndProductId(4L, 3L)).thenReturn(Optional.of(item));
        when(cartItemRepository.findItemsWithProductsByUserId(1L)).thenReturn(List.of(item));

        // WHEN
        var response = service.updateItem(1L, 3L, updateCartItemRequest(quantity));

        // THEN
        assertEquals(quantity, item.getQuantity());
        assertEquals(quantity, response.totalQuantity());
        assertFalse(response.items().getFirst().available());
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#booleanValues")
    void shouldRejectUpdate_When_CartOrItemIsMissing(boolean cartExists) {
        // GIVEN
        when(cartRepository.findByUserId(1L)).thenReturn(cartExists ? Optional.of(cart()) : Optional.empty());

        // WHEN
        var error = assertThrows(ApiException.class,
                () -> service.updateItem(1L, 3L, updateCartItemRequest(1)));

        // THEN
        assertApiError(error, NOT_FOUND, "Item not found in cart");
        verify(cartRepository, never()).save(any());
    }

    @Test
    void shouldDeleteItem_When_ItemBelongsToUserCart() {
        // GIVEN
        var item = item(1);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(item.getCart()));
        when(cartItemRepository.findByCartIdAndProductId(4L, 3L)).thenReturn(Optional.of(item));

        // WHEN
        var response = service.removeItem(1L, 3L);

        // THEN
        verify(cartItemRepository, times(1)).delete(item);
        assertTrue(response.items().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#booleanValues")
    void shouldSucceedWithoutDeleting_When_CartOrItemMissing(boolean cartExists) {
        // GIVEN
        when(cartRepository.findByUserId(1L)).thenReturn(cartExists ? Optional.of(cart()) : Optional.empty());

        // WHEN
        var response = service.removeItem(1L, 3L);

        // THEN
        assertTrue(response.items().isEmpty());
        verify(cartItemRepository, never()).delete(any());
        verify(cartRepository, never()).save(any());
    }

    @Test
    void shouldReturnProductQuantityPairs_When_ItemsRequestedByLaterServices() {
        // GIVEN
        var item = item(7);
        when(cartItemRepository.findItemsWithProductsByUserId(1L)).thenReturn(List.of(item));

        // WHEN
        var result = service.getItems(1L);

        // THEN
        assertEquals(List.of(cartProduct(item.getProduct(), 7)), result);
        verifyNoInteractions(cartRepository, userRepository);
    }

    @Test
    void shouldReturnEmptyPairs_When_NoCartExists() {
        // GIVEN
        when(cartItemRepository.findItemsWithProductsByUserId(1L)).thenReturn(List.of());

        // WHEN
        var result = service.getItems(1L);

        // THEN
        assertTrue(result.isEmpty());
        verifyNoInteractions(cartRepository, userRepository);
    }

    @ParameterizedTest
    @MethodSource("com.egastro.testData.TestCases#booleanValues")
    void shouldClearOnlyExistingCart_When_CheckoutClearsItems(boolean exists) {
        // GIVEN
        when(cartRepository.findByUserId(1L)).thenReturn(exists ? Optional.of(cart()) : Optional.empty());

        // WHEN
        service.clearItems(1L);

        // THEN
        verify(cartItemRepository, times(exists ? 1 : 0)).deleteByCartId(4L);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void shouldRemoveAllProductReferences_When_ProductDeletionEventArrives() {
        // GIVEN
        var event = productDeletedEvent();

        // WHEN
        service.onProductDeleted(event);

        // THEN
        verify(cartItemRepository, times(1)).deleteByProductId(3L);
    }
}
