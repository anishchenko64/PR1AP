import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Практична робота 1. Варіант 1 - Інтернет-замовлення.
 * Аніщенко Артем, ТВ-43.
 *
 * Що робить програма:
 *  - адміністратор додає товари на сайт
 *  - покупці заходять на сайт і купують товари
 *  - наявність товарів рахує семафор (1 дозвіл = 1 товар)
 *  - магазин працює з 9:00 до 18:00
 *  - якщо товару нема, покупець трохи чекає і йде
 *
 * Час прискорений: 1 година магазину = 1 секунда реального часу.
 */
public class OnlineShop {

    static final int OPEN_HOUR = 9;
    static final int CLOSE_HOUR = 18;
    static final int MS_PER_HOUR = 1000;

    // семафор - це кількість товарів на складі (спочатку 0)
    static Semaphore goods = new Semaphore(0);

    // час старту програми, від нього рахуємо "години магазину"
    static long startTime = System.currentTimeMillis();

    // лічильники для підсумку
    static int added = 0;
    static int sold = 0;
    static int leftWithNothing = 0;
    static int shopClosed = 0;

    // поточна година магазину (симуляція починається о 8:00)
    static int getHour() {
        long passed = System.currentTimeMillis() - startTime;
        return 8 + (int) (passed / MS_PER_HOUR);
    }

    static boolean isOpen() {
        int h = getHour();
        return h >= OPEN_HOUR && h < CLOSE_HOUR;
    }

    // синхронізований вивід, щоб рядки не змішувались
    static synchronized void print(String who, String text) {
        System.out.println("[" + getHour() + ":00] " + who + ": " + text);
    }

    // методи для лічильників теж synchronized, бо їх змінюють різні потоки
    static synchronized void addCount() { added++; }
    static synchronized void soldCount() { sold++; }
    static synchronized void nothingCount() { leftWithNothing++; }
    static synchronized void closedCount() { shopClosed++; }

    // ---------- Адміністратор ----------
    static class Admin implements Runnable {
        public void run() {
            try {
                // чекаємо поки магазин відкриється
                while (!isOpen()) {
                    Thread.sleep(100);
                }
                print("Адмін", "магазин відкрився, додаю товари");

                // кожні 2 години додаємо 1 товар
                while (isOpen()) {
                    goods.release();   // з'явився товар
                    addCount();
                    print("Адмін", "додав товар на сайт (в наявності " + goods.availablePermits() + ")");
                    Thread.sleep(2 * MS_PER_HOUR);
                }
                print("Адмін", "магазин зачинився, товари більше не додаю");
            } catch (InterruptedException e) {
                print("Адмін", "роботу перервано");
            }
        }
    }

    // ---------- Покупець ----------
    static class Buyer implements Runnable {
        int id;
        int arriveDelay; // через скільки мс після старту він зайде на сайт

        Buyer(int id, int arriveDelay) {
            this.id = id;
            this.arriveDelay = arriveDelay;
        }

        public void run() {
            String name = "Покупець " + id;
            try {
                Thread.sleep(arriveDelay);

                // перевіряємо робочі години
                if (!isOpen()) {
                    print(name, "зайшов на сайт, але магазин зачинений (9:00-18:00)");
                    closedCount();
                    return;
                }

                print(name, "хоче купити товар");

                // пробуємо взяти товар, чекаємо максимум 2 години
                boolean ok = goods.tryAcquire(2 * MS_PER_HOUR, TimeUnit.MILLISECONDS);

                if (ok) {
                    soldCount();
                    print(name, "купив товар");
                } else {
                    nothingCount();
                    print(name, "товару нема в наявності, не дочекався і пішов");
                }
            } catch (InterruptedException e) {
                print(name, "покупку перервано");
            }
        }
    }

    // ---------- main ----------
    public static void main(String[] args) {
        Random rnd = new Random();
        int buyersCount = 12;

        Thread admin = new Thread(new Admin());
        Thread[] buyers = new Thread[buyersCount];
        for (int i = 0; i < buyersCount; i++) {
            // покупці приходять у випадковий час між 8:00 і 20:00
            int delay = rnd.nextInt(12 * MS_PER_HOUR);
            buyers[i] = new Thread(new Buyer(i + 1, delay));
        }

        // стан NEW - потоки створені, але не запущені
        System.out.println("Стан адміністратора: " + admin.getState());
        System.out.println("Стан покупця 1: " + buyers[0].getState());
        System.out.println();

        admin.start();
        for (Thread b : buyers) {
            b.start();
        }

        try {
            // приблизно о 12:00 дивимось стани ще раз
            Thread.sleep(4 * MS_PER_HOUR);
            System.out.println("\n--- Стани о " + getHour() + ":00 ---");
            System.out.println("Адміністратор: " + admin.getState());
            for (int i = 0; i < buyersCount; i++) {
                System.out.println("Покупець " + (i + 1) + ": " + buyers[i].getState());
            }
            System.out.println();

            // чекаємо поки всі закінчать
            admin.join();
            for (Thread b : buyers) {
                b.join();
            }
        } catch (InterruptedException e) {
            System.out.println("Головний потік перервали");
        }

        System.out.println("\nСтан адміністратора в кінці: " + admin.getState());
        System.out.println("Стан покупця 1 в кінці: " + buyers[0].getState());

        System.out.println("\n===== Підсумок дня =====");
        System.out.println("Додано товарів: " + added);
        System.out.println("Продано: " + sold);
        System.out.println("Залишилось на складі: " + goods.availablePermits());
        System.out.println("Не дочекались товару: " + leftWithNothing);
        System.out.println("Прийшли коли магазин зачинений: " + shopClosed);
    }
}
